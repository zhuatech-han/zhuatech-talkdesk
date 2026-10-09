// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let library;
async function sdk() {
  library ||= await import("livekit-client");
  return library;
}
import { ref } from "vue";
import { api } from "./api.js";
import { t } from "./ui.js";
export const voiceState = ref("disconnected"),
  voiceMembers = ref([]),
  micEnabled = ref(false),
  canHear = ref(true),
  voiceRoomId = ref(null),
  voiceError = ref("");
export const voiceMode = ref("auto");
export const voiceStats = ref({ packets: 0, bytes: 0, relay: false });
let statsTimer;
let room = null,
  lease = null;
function update() {
  if (!room) return;
  voiceMembers.value = [
    room.localParticipant,
    ...room.remoteParticipants.values(),
  ].map((p) => ({
    identity: p.identity,
    name: p.name || p.identity,
    speaking: p.isSpeaking,
    muted: !p.isMicrophoneEnabled,
    quality: p.connectionQuality,
  }));
  micEnabled.value = room.localParticipant.isMicrophoneEnabled;
}
/** 默认静音加入；音频元素绑定真实远端轨道，不采集麦克风。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function connectVoice(id) {
  await disconnectVoice();
  voiceError.value = "";
  voiceState.value = "connecting";
  let next;
  try {
    lease = await api(`/rooms/${id}/voice`, { method: "POST" });
    const { Room, RoomEvent, Track } = await sdk();
    next = new Room({
      singlePeerConnection: false,
      adaptiveStream: false,
      dynacast: false,
    });
    room = next;
    voiceRoomId.value = id;
    next.on(RoomEvent.TrackSubscribed, (track) => {
      if (track.kind === Track.Kind.Audio) {
        const audio = track.attach();
        audio.dataset.voiceAudio = "true";
        document.querySelector("#voice-audio")?.appendChild(audio);
      }
      update();
    });
    next.on(RoomEvent.TrackUnsubscribed, (track) =>
      track.detach().forEach((e) => e.remove()),
    );
    for (const event of [
      RoomEvent.ParticipantConnected,
      RoomEvent.ParticipantDisconnected,
      RoomEvent.ActiveSpeakersChanged,
      RoomEvent.TrackMuted,
      RoomEvent.TrackUnmuted,
      RoomEvent.LocalTrackPublished,
      RoomEvent.LocalTrackUnpublished,
      RoomEvent.ConnectionQualityChanged,
    ])
      next.on(event, update);
    next.on(RoomEvent.AudioPlaybackStatusChanged, () => {
      canHear.value = next.canPlaybackAudio;
    });
    next.on(RoomEvent.Reconnecting, () => {
      voiceState.value = "reconnecting";
    });
    next.on(RoomEvent.Reconnected, () => {
      voiceState.value = "connected";
      update();
    });
    next.on(RoomEvent.Disconnected, () => {
      if (room === next) {
        clearInterval(statsTimer);
        voiceState.value = "disconnected";
        voiceMembers.value = [];
        micEnabled.value = false;
        voiceRoomId.value = null;
        voiceError.value = t(
          "语音连接已结束，请检查房间状态后重新加入。",
          "Voice connection ended. Check room access and rejoin.",
        );
      }
    });
    const url = new URL(lease.url, window.location.origin);
    url.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    await next.connect(url.href, lease.token, {
      rtcConfig: {
        iceTransportPolicy: voiceMode.value === "relay" ? "relay" : "all",
      },
    });
    statsTimer = setInterval(async () => {
      let packets = 0,
        bytes = 0,
        relay = false;
      for (const p of next.remoteParticipants.values())
        for (const pub of p.audioTrackPublications.values()) {
          const report = await pub.track?.getRTCStatsReport().catch(() => null);
          if (!report) continue;
          for (const row of report.values()) {
            if (row.type === "inbound-rtp" && row.kind === "audio") {
              packets += row.packetsReceived || 0;
              bytes += row.bytesReceived || 0;
            }
            if (row.type === "transport" && row.selectedCandidatePairId) {
              const pair = report.get(row.selectedCandidatePairId);
              if (
                pair &&
                report.get(pair.localCandidateId)?.candidateType === "relay"
              )
                relay = true;
            }
          }
        }
      voiceStats.value = { packets, bytes, relay };
    }, 2000);
    await next.startAudio();
    voiceState.value = "connected";
    update();
  } catch (e) {
    await disconnectVoice();
    if (e.status !== undefined) throw e;
    throw new Error("VOICE_CONNECT_FAILED");
  }
}
/** 离开释放媒体设备并撤销服务端租约；撤销失败不重放写操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function disconnectVoice() {
  clearInterval(statsTimer);
  voiceStats.value = { packets: 0, bytes: 0, relay: false };
  const prior = lease;
  lease = null;
  const previous = room;
  room = null;
  if (previous) await previous.disconnect();
  document.querySelectorAll("[data-voice-audio]").forEach((e) => e.remove());
  voiceState.value = "disconnected";
  voiceRoomId.value = null;
  voiceMembers.value = [];
  micEnabled.value = false;
  if (prior)
    try {
      await api(`/voice/${prior.leaseId}/leave`, { method: "POST" });
    } catch {
      /*服务器巡检清理已离开的租约，不保存密码或令牌*/
    }
}
/** 点击后才请求麦克风，拒绝权限不会把UI误标为已开麦。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function toggleMicrophone() {
  if (!room || voiceState.value !== "connected") return;
  voiceError.value = "";
  try {
    await room.localParticipant.setMicrophoneEnabled(!micEnabled.value);
    update();
  } catch (e) {
    voiceError.value =
      e.name === "NotAllowedError"
        ? t(
            "麦克风权限被拒绝。请在浏览器站点设置中允许后重试。",
            "Microphone denied. Allow it in browser site settings and retry.",
          )
        : e.name === "NotFoundError"
          ? t(
              "没有找到麦克风，请连接设备后重试。",
              "No microphone found. Connect a device and retry.",
            )
          : t(
              "麦克风无法启用，请检查设备是否被占用。",
              "Microphone unavailable. Check whether another application uses it.",
            );
  }
}
/** 处理浏览器自动播放限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function enableAudio() {
  if (room) {
    await room.startAudio();
    canHear.value = room.canPlaybackAudio;
  }
}
/** 当前设备枚举；设备名称遵守浏览器授权，不提前请求录音。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function microphones() {
  const { Room } = await sdk();
  return Room.getLocalDevices("audioinput", false);
}
/** 切换输入设备并沿用当前静音状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function switchMicrophone(id) {
  if (room) await room.switchActiveDevice("audioinput", id);
}
