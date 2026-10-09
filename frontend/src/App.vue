<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue";
import {
  Hash,
  Plus,
  Search,
  Send,
  Mic,
  MicOff,
  Headphones,
  PhoneOff,
  Users,
  Settings,
  LogOut,
  Globe,
  X,
  Link,
  ArrowLeft,
  Shield,
  MessageSquare,
  BarChart3,
  FileText,
  RefreshCw,
} from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import {
  t,
  language,
  errorMessage,
  confirmation,
  ask,
  answerConfirmation,
  dateTime,
  actionName,
} from "./ui.js";
import {
  connectVoice,
  disconnectVoice,
  voiceState,
  voiceMembers,
  micEnabled,
  voiceRoomId,
  voiceError,
  voiceMode,
  voiceStats,
  canHear,
  toggleMicrophone,
  enableAudio,
  microphones,
  switchMicrophone,
} from "./voice.js";
import AdminPanel from "./components/AdminPanel.vue";
const me = ref(null),
  ready = ref(false),
  pending = ref(false),
  message = ref(""),
  username = ref(""),
  password = ref(""),
  section = ref("chat");
const rooms = ref([]),
  selected = ref(null),
  messages = ref([]),
  members = ref([]),
  options = ref({ categories: [], capacity: 16, messageLimit: 2000 }),
  search = ref(""),
  stateFilter = ref("ALL"),
  sort = ref("name");
const dialog = ref(null),
  form = ref({}),
  inviteCode = ref(""),
  inviteRows = ref([]),
  draft = ref(""),
  older = ref([]),
  nonce = ref(null),
  showMembers = ref(false),
  mobileOpen = ref(false),
  adminRef = ref(null),
  stats = ref({}),
  audit = ref([]),
  devices = ref([]);
const navIcons = {
  chat: MessageSquare,
  dashboard: BarChart3,
  users: Users,
  roles: Shield,
  settings: Settings,
  audit: FileText,
};
const filtered = computed(() =>
  rooms.value
    .filter(
      (r) =>
        r.name.toLowerCase().includes(search.value.toLowerCase()) &&
        (stateFilter.value === "ALL" || r.status === stateFilter.value),
    )
    .sort((a, b) =>
      sort.value === "name" ? a.name.localeCompare(b.name) : b.id - a.id,
    ),
);
const current = computed(() =>
  rooms.value.find((r) => r.id === selected.value),
);
const statusName = (s) =>
  ({
    OPEN: t("开放", "Open"),
    LOCKED: t("已锁定", "Locked"),
    ARCHIVED: t("已归档", "Archived"),
  })[s] || s;
const voiceName = computed(
  () =>
    ({
      disconnected: t("未连接", "Disconnected"),
      connecting: t("连接中…", "Connecting…"),
      connected: t("语音已连接", "Voice connected"),
      reconnecting: t("正在重连…", "Reconnecting…"),
    })[voiceState.value],
);
let timer;
function fail(e) {
  message.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    password.value = "";
    resetApi();
    void disconnectVoice();
  }
}
/** 写入互斥，不静默重放未知结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  try {
    return await api(path, { method, body });
  } catch (e) {
    fail(e);
    return null;
  } finally {
    pending.value = false;
  }
}
async function loadRooms() {
  rooms.value = await api("/rooms");
  if (selected.value && !current.value) {
    selected.value = null;
    messages.value = [];
    members.value = [];
    if (voiceRoomId.value) await disconnectVoice();
  }
}
async function login() {
  const p = password.value;
  password.value = "";
  const result = await perform("/auth/login", "POST", {
    username: username.value,
    password: p,
  });
  if (result) {
    me.value = result;
    resetApi();
    section.value = result.menus[0]?.code || "chat";
    await load();
  }
}
async function load() {
  try {
    me.value = await api("/auth/me");
    if (section.value === "chat") {
      await loadRooms();
      options.value = await api("/options");
    } else if (section.value === "dashboard") stats.value = await api("/stats");
    else if (section.value === "audit")
      audit.value = (await api("/audit")).reverse();
  } catch (e) {
    fail(e);
  }
}
async function select(r) {
  message.value = "";
  if (
    draft.value &&
    !(await ask(t("放弃未发送消息？", "Discard unsent message?")))
  )
    return;
  if (!r.joined) {
    const x = await perform(`/rooms/${r.id}/join`, "POST");
    if (!x) return;
    await loadRooms();
  }
  selected.value = r.id;
  draft.value = "";
  nonce.value = null;
  older.value = [];
  mobileOpen.value = false;
  await refreshRoom();
}
async function refreshRoom() {
  if (!selected.value) return;
  try {
    const id = selected.value;
    const [m, u] = await Promise.all([
      api(`/rooms/${id}/messages`),
      api(`/rooms/${id}/members`),
    ]);
    if (id === selected.value) {
      messages.value = m;
      members.value = u;
    }
  } catch (e) {
    fail(e);
    if (e.status === 403) {
      await disconnectVoice();
      selected.value = null;
      await loadRooms();
    }
  }
}
async function switchSection(s) {
  if (adminRef.value && !(await adminRef.value.canLeave())) return;
  if (
    draft.value &&
    !(await ask(t("放弃未发送消息？", "Discard unsent message?")))
  )
    return;
  draft.value = "";
  section.value = s;
  message.value = "";
  mobileOpen.value = false;
  await load();
}
async function logout() {
  await disconnectVoice();
  if (await perform("/auth/logout", "POST")) {
    me.value = null;
    selected.value = null;
    rooms.value = [];
    resetApi();
  }
}
function editRoom(r) {
  form.value = r
    ? { ...r }
    : {
        name: "",
        description: "",
        category: options.value.categories[0]?.code || "",
        visibility: "PRIVATE",
        capacity: options.value.capacity,
      };
  dialog.value = "room";
}
async function saveRoom() {
  const f = form.value;
  const x = await perform(
    `/rooms${f.id ? "/" + f.id : ""}`,
    f.id ? "PUT" : "POST",
    f,
  );
  if (x) {
    dialog.value = null;
    await loadRooms();
    selected.value = x.id;
    await refreshRoom();
  }
}
async function stateRoom(s) {
  if (
    !(await ask(
      s === "ARCHIVED"
        ? t(
            "归档后不能重新开放。确认归档此房间？",
            "Archiving is permanent. Archive this room?",
          )
        : s === "LOCKED"
          ? t(
              "锁定后停止发送和语音通话。确认锁定？",
              "Locking stops sending and voice calls. Lock room?",
            )
          : t("重新开放此房间？", "Reopen this room?"),
    ))
  )
    return;
  if (
    await perform(`/rooms/${selected.value}/state`, "POST", {
      version: current.value.version,
      status: s,
    })
  ) {
    await loadRooms();
    await refreshRoom();
    if (s !== "OPEN" && voiceRoomId.value === selected.value)
      await disconnectVoice();
  }
}
async function leaveRoom() {
  if (
    !(await ask(
      t(
        "离开房间后无法查阅其聊天记录。确认离开？",
        "Leaving removes access to room history. Leave?",
      ),
    ))
  )
    return;
  if (await perform(`/rooms/${selected.value}/leave`, "POST")) {
    if (voiceRoomId.value === selected.value) await disconnectVoice();
    selected.value = null;
    await loadRooms();
  }
}
async function send() {
  if (!draft.value.trim()) return;
  nonce.value ||= crypto.randomUUID();
  const x = await perform(`/rooms/${selected.value}/messages`, "POST", {
    content: draft.value,
    nonce: nonce.value,
  });
  if (x) {
    draft.value = "";
    nonce.value = null;
    older.value = [];
    await refreshRoom();
    await nextTick();
    document.querySelector("#chat-end")?.scrollIntoView({ block: "nearest" });
  }
}
async function removeMessage(m) {
  if (await ask(t("移除这条消息正文？", "Remove this message?"))) {
    if (await perform(`/rooms/${selected.value}/messages/${m.id}`, "DELETE")) {
      older.value = [];
      await refreshRoom();
    }
  }
}
async function loadOlder() {
  try {
    const first = older.value[0]?.id || messages.value[0]?.id;
    if (!first) return;
    const rows = await api(`/rooms/${selected.value}/messages?before=${first}`);
    older.value = [...rows, ...older.value];
    if (!rows.length)
      message.value = t("已到最早消息。", "No earlier messages.");
  } catch (e) {
    fail(e);
  }
}
async function invite() {
  inviteCode.value = "";
  dialog.value = "invites";
  form.value = { maxUses: 1 };
  try {
    inviteRows.value = await api(`/rooms/${selected.value}/invites`);
  } catch (e) {
    fail(e);
  }
}
async function makeInvite() {
  const x = await perform(
    `/rooms/${selected.value}/invites`,
    "POST",
    form.value,
  );
  if (x) {
    inviteCode.value = x.code;
    inviteRows.value = await api(`/rooms/${selected.value}/invites`);
  }
}
async function revoke(i) {
  if (await perform(`/rooms/${selected.value}/invites/${i.id}/revoke`, "POST"))
    inviteRows.value = await api(`/rooms/${selected.value}/invites`);
}
async function redeem() {
  const x = await perform("/invites/redeem", "POST", form.value);
  if (x) {
    dialog.value = null;
    form.value = {};
    await loadRooms();
    await select(x);
  }
}
async function memberAction(m, s) {
  if (
    await ask(
      s === "BANNED"
        ? t(
            "禁止该成员进入，并结束其语音连接？",
            "Ban this member and end their voice connections?",
          )
        : t(
            "解除禁入？成员需要重新加入。",
            "Lift the ban? The member must rejoin.",
          ),
    )
  ) {
    if (
      await perform(`/rooms/${selected.value}/members/${m.accountId}`, "PUT", {
        status: s,
      })
    ) {
      await refreshRoom();
      await loadRooms();
    }
  }
}
async function transfer(m) {
  if (
    await ask(
      t("将房主转让给该成员？", "Transfer room ownership to this member?"),
    )
  ) {
    if (
      await perform(`/rooms/${selected.value}/owner`, "POST", {
        accountId: m.accountId,
      })
    ) {
      await loadRooms();
      await refreshRoom();
    }
  }
}
async function joinVoice() {
  if (pending.value) return;
  pending.value = true;
  message.value = "";
  try {
    await connectVoice(selected.value);
  } catch (e) {
    fail(e);
  } finally {
    pending.value = false;
  }
}
async function deviceDialog() {
  dialog.value = "devices";
  try {
    devices.value = await microphones();
  } catch {
    devices.value = [];
  }
}
async function changeDevice(e) {
  try {
    await switchMicrophone(e.target.value);
  } catch {
    message.value = t(
      "切换设备失败，请重新连接后再试。",
      "Could not switch device. Reconnect and retry.",
    );
  }
}
async function changePassword() {
  if (await perform("/auth/password", "POST", form.value)) {
    await disconnectVoice();
    me.value = null;
    dialog.value = null;
    form.value = {};
    resetApi();
  }
}
async function exportRooms() {
  try {
    await download("/reports/rooms.csv", "talkdesk-rooms.csv");
  } catch (e) {
    fail(e);
  }
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    section.value = me.value.menus[0]?.code || "chat";
    await load();
  } catch (e) {
    if (e.status !== 401) fail(e);
  }
  ready.value = true;
  timer = setInterval(async () => {
    if (!me.value || pending.value || dialog.value) return;
    try {
      if (section.value === "chat") {
        await loadRooms();
        await refreshRoom();
      }
    } catch (e) {
      fail(e);
    }
  }, 3000);
});
onUnmounted(() => {
  clearInterval(timer);
  void disconnectVoice();
});
</script>
<template>
  <div v-if="!ready" class="loading">{{ t("正在连接…", "Connecting…") }}</div>
  <div v-else-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><strong>TalkDesk</strong>
      <p>{{ t("语音聊天", "Voice chat") }}</p>
      <div class="login-line"></div>
      <span>{{
        t("团队 · 社群 · 自有部署", "Teams · Communities · Self-hosted")
      }}</span>
    </section>
    <main class="login-main">
      <button
        class="language"
        @click="language = language === 'zh' ? 'en' : 'zh'"
      >
        <Globe :size="16" />{{ language === "zh" ? "English" : "中文" }}
      </button>
      <form class="login-form" @submit.prevent="login">
        <h1>{{ t("登录 TalkDesk", "Sign in to TalkDesk") }}</h1>
        <p class="muted">
          {{
            t(
              "使用管理员分配的账号。",
              "Use an account provided by your administrator.",
            )
          }}
        </p>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="username"
            required
            maxlength="60"
            autocomplete="username"
            :disabled="pending" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="password"
            type="password"
            required
            maxlength="128"
            autocomplete="current-password"
            :disabled="pending" /></label
        ><button class="primary full" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <footer class="login-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技", "ZhiHua Technology")
        }}</a
        ><span>{{
          t(
            "公开源码学习版 · 商用需授权",
            "Source-available learning edition · Commercial license required",
          )
        }}</span>
      </footer>
    </main>
  </div>
  <div v-else class="app-shell">
    <aside class="rail">
      <img src="/brand/logo.jpg" alt="知华科技" /><button
        v-for="m in me.menus"
        :key="m.id"
        :class="{ active: section === m.code }"
        :title="language === 'zh' ? m.name : m.nameEn"
        :aria-label="language === 'zh' ? m.name : m.nameEn"
        @click="switchSection(m.code)"
      >
        <component :is="navIcons[m.code] || Settings" :size="22" />
      </button>
      <div class="rail-spacer"></div>
      <button :aria-label="t('关于系统', 'About')" @click="dialog = 'about'">
        <Shield :size="20" /></button
      ><button :aria-label="t('退出登录', 'Sign out')" @click="logout">
        <LogOut :size="20" />
      </button>
    </aside>
    <main class="main-shell">
      <header class="topbar">
        <button
          class="mobile-toggle"
          :aria-label="t('显示房间列表', 'Show rooms')"
          @click="mobileOpen = !mobileOpen"
        >
          <Hash :size="20" /></button
        ><strong>TalkDesk</strong
        ><span class="top-subtitle">{{ t("语音聊天", "Voice chat") }}</span>
        <div class="spacer"></div>
        <button
          class="text-button"
          @click="language = language === 'zh' ? 'en' : 'zh'"
        >
          {{ language === "zh" ? "EN" : "中文" }}</button
        ><button
          class="user-button"
          @click="
            form = { oldPassword: '', newPassword: '' };
            dialog = 'password';
          "
        >
          <span class="avatar small">{{ me.displayName.slice(0, 1) }}</span
          >{{ me.displayName }}
        </button>
      </header>
      <p v-if="message" class="global-error" role="alert">
        {{ message
        }}<button :aria-label="t('关闭提示', 'Dismiss')" @click="message = ''">
          <X :size="16" />
        </button>
      </p>
      <div
        v-if="section === 'chat'"
        class="chat-layout"
        :class="{ 'mobile-open': mobileOpen }"
      >
        <aside class="room-sidebar">
          <header>
            <h2>{{ t("房间", "Rooms") }}</h2>
            <button
              v-if="me.permissions.includes('rooms')"
              :aria-label="t('创建房间', 'Create room')"
              @click="editRoom(null)"
            >
              <Plus :size="18" />
            </button>
          </header>
          <label class="search-box"
            ><Search :size="16" /><input
              v-model="search"
              :placeholder="t('搜索房间', 'Search rooms')"
              :aria-label="t('搜索房间', 'Search rooms')"
          /></label>
          <div class="room-filters">
            <select
              v-model="stateFilter"
              :aria-label="t('房间状态', 'Room status')"
            >
              <option value="ALL">{{ t("全部状态", "All states") }}</option>
              <option value="OPEN">{{ t("开放", "Open") }}</option>
              <option value="LOCKED">{{ t("锁定", "Locked") }}</option>
              <option value="ARCHIVED">
                {{ t("归档", "Archived") }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="name">{{ t("名称", "Name") }}</option>
              <option value="recent">{{ t("最新", "Newest") }}</option>
            </select>
          </div>
          <div class="room-list">
            <button
              v-for="r in filtered"
              :key="r.id"
              class="room-row"
              :class="{ selected: r.id === selected }"
              @click="select(r)"
            >
              <Hash :size="19" /><span
                ><strong>{{ r.name }}</strong
                ><small
                  >{{ r.members }} / {{ r.capacity }} ·
                  {{
                    r.visibility === "PRIVATE"
                      ? t("私有", "Private")
                      : t("组织", "Organization")
                  }}<template v-if="r.status !== 'OPEN'">
                    · {{ statusName(r.status) }}</template
                  ></small
                ></span
              ><span v-if="!r.joined" class="join-hint">{{
                t("加入", "Join")
              }}</span>
            </button>
            <p v-if="!filtered.length" class="empty-small">
              {{ t("暂无可见房间", "No visible rooms") }}
            </p>
          </div>
          <button
            class="invite-join"
            @click="
              form = { code: '' };
              dialog = 'redeem';
            "
          >
            <Link :size="16" />{{ t("使用邀请加入", "Join with invitation") }}
          </button>
          <div v-if="voiceRoomId" class="voice-dock">
            <span class="connection-dot"></span><strong>{{ voiceName }}</strong
            ><small>{{
              rooms.find((r) => r.id === voiceRoomId)?.name ||
              t("语音房", "Voice room")
            }}</small>
            <div>
              <button
                :aria-label="t('切换麦克风', 'Toggle microphone')"
                @click="toggleMicrophone"
              >
                <component :is="micEnabled ? Mic : MicOff" :size="18" /></button
              ><button
                :aria-label="t('离开语音', 'Leave voice')"
                @click="disconnectVoice"
              >
                <PhoneOff :size="18" />
              </button>
            </div>
          </div>
        </aside>
        <section v-if="current" class="conversation">
          <header class="conversation-header">
            <button
              class="mobile-toggle"
              :aria-label="t('返回房间', 'Back to rooms')"
              @click="mobileOpen = true"
            >
              <ArrowLeft :size="18" /></button
            ><Hash :size="22" />
            <div>
              <h1>{{ current.name }}</h1>
              <p class="muted">
                {{ current.description || statusName(current.status) }}
              </p>
            </div>
            <div class="spacer"></div>
            <button
              :aria-label="t('成员列表', 'Members')"
              @click="showMembers = !showMembers"
            >
              <Users :size="18" />{{ current.members }}</button
            ><button
              v-if="current.canManage"
              :aria-label="t('房间设置', 'Room settings')"
              @click="editRoom(current)"
            >
              <Settings :size="18" />
            </button>
          </header>
          <div class="voice-strip">
            <Headphones :size="20" />
            <div>
              <strong>{{
                voiceRoomId === selected
                  ? voiceName
                  : t("房间语音", "Room voice")
              }}</strong
              ><small>{{
                voiceRoomId === selected
                  ? t("点击麦克风可开启发言", "Enable your microphone to speak")
                  : t("加入时默认静音", "Join with your microphone muted")
              }}</small>
            </div>
            <div class="spacer"></div>
            <button
              v-if="voiceRoomId !== selected"
              class="primary"
              :disabled="pending || current.status !== 'OPEN'"
              @click="joinVoice"
            >
              {{ t("加入语音", "Join voice") }}</button
            ><template v-else
              ><button
                :class="{ primary: micEnabled }"
                :disabled="voiceState !== 'connected'"
                @click="toggleMicrophone"
              >
                <component :is="micEnabled ? Mic : MicOff" :size="17" />{{
                  micEnabled
                    ? t("静音", "Mute")
                    : t("开启麦克风", "Enable microphone")
                }}</button
              ><button class="danger-text" @click="disconnectVoice">
                <PhoneOff :size="18" /></button
            ></template>
          </div>
          <p v-if="voiceError" class="inline-error" role="alert">
            {{ voiceError }}
          </p>
          <button
            v-if="!canHear && voiceRoomId"
            class="audio-unlock"
            @click="enableAudio"
          >
            {{ t("点击开启声音播放", "Click to enable audio playback") }}
          </button>
          <div v-if="voiceRoomId === selected" class="voice-people">
            <div
              v-for="p in voiceMembers"
              :key="p.identity"
              class="voice-person"
              :class="{ speaking: p.speaking }"
            >
              <span class="avatar">{{ p.name.slice(0, 1) }}</span
              ><span
                >{{ p.name
                }}<small>{{
                  p.speaking
                    ? t("发言中", "Speaking")
                    : p.muted
                      ? t("静音 / 收听", "Muted / listening")
                      : t("麦克风已开启", "Microphone on")
                }}</small></span
              ><MicOff v-if="p.muted" :size="14" />
            </div>
            <button class="text-button" @click="deviceDialog">
              {{ t("音频设备", "Audio devices") }}
            </button>
          </div>
          <div class="message-list">
            <button
              v-if="messages.length >= 50 || older.length"
              class="older"
              @click="loadOlder"
            >
              {{ t("加载更早消息", "Load older messages") }}
            </button>
            <p v-if="!messages.length" class="empty-small">
              {{ t("还没有消息。", "No messages yet.") }}
            </p>
            <article
              v-for="m in [...older, ...messages]"
              :key="m.id"
              class="chat-message"
            >
              <span class="avatar">{{ m.author.slice(0, 1) }}</span>
              <div>
                <header>
                  <strong>{{ m.author }}</strong
                  ><time>{{ dateTime(m.createdAt) }}</time
                  ><button
                    v-if="
                      !m.removed && (m.authorId === me.id || current.canManage)
                    "
                    class="message-remove"
                    :aria-label="t('移除消息', 'Remove message') + ' ' + m.id"
                    @click="removeMessage(m)"
                  >
                    <X :size="14" />
                  </button>
                </header>
                <p :class="{ muted: m.removed }">
                  {{
                    m.removed ? t("消息已移除", "Message removed") : m.content
                  }}
                </p>
              </div>
            </article>
            <div id="chat-end"></div>
          </div>
          <form class="composer" @submit.prevent="send">
            <textarea
              v-model="draft"
              :aria-label="t('消息内容', 'Message')"
              :placeholder="
                current.status === 'OPEN'
                  ? t('发送消息到 ' + current.name, 'Message ' + current.name)
                  : t('房间已锁定或归档', 'Room is locked or archived')
              "
              :maxlength="options.messageLimit"
              :disabled="pending || current.status !== 'OPEN'"
              rows="2"
              @input="nonce = null"
            ></textarea>
            <footer>
              <small>{{ draft.length }} / {{ options.messageLimit }}</small
              ><span class="spacer"></span
              ><button
                class="primary"
                :disabled="
                  pending || !draft.trim() || current.status !== 'OPEN'
                "
              >
                <Send :size="16" />{{ t("发送", "Send") }}
              </button>
            </footer>
          </form>
          <div
            v-if="current.canManage || current.ownerId !== me.id"
            class="room-actions"
          >
            <template v-if="current.canManage"
              ><button :disabled="current.status !== 'OPEN'" @click="invite">
                {{ t("邀请成员", "Invite members") }}</button
              ><button
                v-if="current.status === 'OPEN'"
                @click="stateRoom('LOCKED')"
              >
                {{ t("锁定", "Lock") }}</button
              ><button
                v-if="current.status === 'LOCKED'"
                @click="stateRoom('OPEN')"
              >
                {{ t("重新开放", "Reopen") }}</button
              ><button
                v-if="current.status !== 'ARCHIVED'"
                @click="stateRoom('ARCHIVED')"
              >
                {{ t("归档", "Archive") }}
              </button></template
            ><button v-if="current.ownerId !== me.id" @click="leaveRoom">
              {{ t("离开房间", "Leave room") }}
            </button>
          </div>
        </section>
        <section v-else class="conversation welcome">
          <Headphones :size="42" />
          <h1>{{ t("选择一个房间", "Choose a room") }}</h1>
          <p>
            {{
              t(
                "加入语音，或发送文字消息。",
                "Join a voice conversation or send a message.",
              )
            }}
          </p>
          <button
            v-if="me.permissions.includes('rooms')"
            class="primary"
            @click="editRoom(null)"
          >
            <Plus :size="16" />{{ t("创建房间", "Create room") }}
          </button>
        </section>
        <aside v-if="showMembers && current" class="member-sidebar">
          <header>
            <h2>{{ t("成员", "Members") }}</h2>
            <button
              :aria-label="t('关闭成员列表', 'Close members')"
              @click="showMembers = false"
            >
              <X :size="17" />
            </button>
          </header>
          <div v-for="m in members" :key="m.accountId" class="member-row">
            <span class="avatar">{{ m.name.slice(0, 1) }}</span>
            <div>
              <strong>{{ m.name }}</strong
              ><small>{{
                m.owner
                  ? t("房主", "Owner")
                  : m.status === "BANNED"
                    ? t("已禁入", "Banned")
                    : m.status === "LEFT"
                      ? t("已离开", "Left")
                      : m.enabled
                        ? t("成员", "Member")
                        : t("账号停用", "Account disabled")
              }}</small
              ><template v-if="current.canManage && !m.owner"
                ><button
                  v-if="m.status === 'ACTIVE'"
                  class="text-button"
                  @click="memberAction(m, 'BANNED')"
                >
                  {{ t("禁入", "Ban") }}</button
                ><button
                  v-else-if="m.status === 'BANNED'"
                  class="text-button"
                  @click="memberAction(m, 'LEFT')"
                >
                  {{ t("解除禁入", "Unban") }}</button
                ><button
                  v-if="m.status === 'ACTIVE' && m.enabled"
                  class="text-button"
                  @click="transfer(m)"
                >
                  {{ t("转让房主", "Transfer ownership") }}
                </button></template
              >
            </div>
          </div>
        </aside>
      </div>
      <div v-else class="admin-content">
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          ref="adminRef"
          :key="section"
          :section="section"
          :pending="pending"
          :message="message"
          :perform="perform"
          @error="fail"
        />
        <section v-else-if="section === 'dashboard'">
          <header class="page-heading">
            <h1>{{ t("使用统计", "Usage statistics") }}</h1>
            <div class="actions">
              <button @click="load">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button @click="exportRooms">
                {{ t("导出房间台账", "Export rooms") }}
              </button>
            </div>
          </header>
          <div class="metrics">
            <div
              v-for="[k, zh, en] in [
                ['rooms', '房间', 'Rooms'],
                ['open', '开放房间', 'Open rooms'],
                ['members', '成员关系', 'Memberships'],
                ['messages', '消息记录', 'Messages'],
              ]"
              :key="k"
            >
              <span>{{ t(zh, en) }}</span
              ><strong>{{ stats[k] ?? 0 }}</strong>
            </div>
          </div>
          <p class="muted">
            {{
              t(
                "统计限于授权组织范围，不读取聊天正文。",
                "Metrics cover authorized organizations; message contents are excluded.",
              )
            }}
          </p>
        </section>
        <section v-else-if="section === 'audit'">
          <header class="page-heading">
            <h1>{{ t("操作记录", "Audit trail") }}</h1>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap panel">
            <table>
              <thead>
                <tr>
                  <th>{{ t("时间", "Time") }}</th>
                  <th>{{ t("账号", "Account") }}</th>
                  <th>{{ t("操作", "Action") }}</th>
                  <th>{{ t("对象", "Object") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in audit.slice(0, 200)" :key="a.id">
                  <td>{{ dateTime(a.createdAt) }}</td>
                  <td>{{ a.actor }}</td>
                  <td>{{ actionName(a.action) }}</td>
                  <td>{{ a.objectId }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!audit.length" class="empty-small">
              {{ t("暂无记录", "No records") }}
            </p>
          </div>
        </section>
      </div>
    </main>
  </div>
  <div id="voice-audio"></div>
  <div v-if="dialog" class="modal-backdrop">
    <section class="modal" role="dialog" aria-modal="true" :aria-label="dialog">
      <header>
        <h2>
          {{
            {
              room: t("房间设置", "Room settings"),
              redeem: t("使用邀请加入", "Join with invitation"),
              invites: t("邀请成员", "Invite members"),
              devices: t("音频设备", "Audio devices"),
              password: t("修改密码", "Change password"),
              about: t("关于 TalkDesk", "About TalkDesk"),
            }[dialog]
          }}
        </h2>
        <button
          :disabled="pending"
          :aria-label="t('关闭', 'Close')"
          @click="
            dialog = null;
            form = {};
            inviteCode = '';
          "
        >
          <X :size="20" />
        </button>
      </header>
      <p v-if="message" class="form-error">{{ message }}</p>
      <form v-if="dialog === 'room'" @submit.prevent="saveRoom">
        <label
          >{{ t("房间名称", "Room name")
          }}<input
            v-model="form.name"
            required
            maxlength="120"
            :disabled="pending" /></label
        ><label
          >{{ t("房间说明", "Description")
          }}<textarea
            v-model="form.description"
            maxlength="1000"
            :disabled="pending"
          ></textarea>
        </label>
        <div class="form-grid">
          <label
            >{{ t("分类", "Category")
            }}<select v-model="form.category" :disabled="pending">
              <option
                v-for="c in options.categories"
                :key="c.id"
                :value="c.code"
              >
                {{ language === "zh" ? c.name : c.nameEn }}
              </option>
            </select></label
          ><label
            >{{ t("人数上限", "Capacity")
            }}<input
              v-model.number="form.capacity"
              type="number"
              min="1"
              :max="options.capacity"
              required
              :disabled="pending"
          /></label>
        </div>
        <label
          >{{ t("可见范围", "Visibility")
          }}<select v-model="form.visibility" :disabled="pending">
            <option value="PRIVATE">
              {{ t("私有：凭邀请加入", "Private: invitation required") }}
            </option>
            <option value="ORGANIZATION">
              {{
                t(
                  "组织：同组织账号可加入",
                  "Organization: eligible accounts can join",
                )
              }}
            </option>
          </select></label
        >
        <footer>
          <button
            class="primary"
            :disabled="pending || form.status === 'ARCHIVED'"
          >
            {{ t("保存", "Save") }}
          </button>
        </footer>
      </form>
      <form v-else-if="dialog === 'redeem'" @submit.prevent="redeem">
        <label
          >{{ t("邀请代码", "Invitation code")
          }}<input
            v-model="form.code"
            required
            maxlength="64"
            autocomplete="off"
            :disabled="pending"
        /></label>
        <footer>
          <button class="primary" :disabled="pending">
            {{ t("加入房间", "Join room") }}
          </button>
        </footer>
      </form>
      <div v-else-if="dialog === 'invites'">
        <form @submit.prevent="makeInvite">
          <label
            >{{ t("最多使用次数", "Maximum uses")
            }}<input
              v-model.number="form.maxUses"
              type="number"
              min="1"
              max="32"
              required
              :disabled="pending" /></label
          ><button class="primary" :disabled="pending">
            {{ t("生成邀请", "Generate invitation") }}
          </button>
        </form>
        <label v-if="inviteCode" class="invitation-result"
          >{{
            t(
              "复制代码给已有登录账号的成员",
              "Copy the code for a member with a login account",
            )
          }}<input
            :value="inviteCode"
            readonly
            :aria-label="t('新邀请代码', 'New invitation code')"
          /><small>{{
            t(
              "关闭后不再显示原文。",
              "The code will not be shown again after closing.",
            )
          }}</small></label
        >
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("有效期", "Expiry") }}</th>
                <th>{{ t("使用量", "Uses") }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="i in inviteRows" :key="i.id">
                <td>{{ dateTime(i.expiresAt) }}</td>
                <td>{{ i.uses }}/{{ i.maxUses }}</td>
                <td>
                  <button
                    v-if="!i.revoked"
                    :disabled="pending"
                    @click="revoke(i)"
                  >
                    {{ t("撤销", "Revoke") }}</button
                  ><span v-else>{{ t("已撤销", "Revoked") }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else-if="dialog === 'devices'">
        <label
          >{{ t("连接方式", "Connection mode")
          }}<select v-model="voiceMode">
            <option value="auto">{{ t("自动", "Automatic") }}</option>
            <option value="relay">{{ t("使用中继", "Relay only") }}</option>
          </select></label
        >
        <button
          :disabled="pending || !current || current.status !== 'OPEN'"
          @click="
            dialog = null;
            joinVoice();
          "
        >
          {{ t("应用并重新连接", "Apply and reconnect") }}
        </button>
        <p class="muted">
          {{ t("已接收音频包", "Received audio packets") }}:
          {{ voiceStats.packets }} ·
          {{ Math.round(voiceStats.bytes / 1024) }} KiB
        </p>

        <label
          >{{ t("麦克风", "Microphone")
          }}<select
            :disabled="voiceState !== 'connected'"
            @change="changeDevice"
          >
            <option value="">{{ t("系统默认", "System default") }}</option>
            <option
              v-for="(d, i) in devices"
              :key="d.deviceId"
              :value="d.deviceId"
            >
              {{ d.label || t("麦克风 " + (i + 1), "Microphone " + (i + 1)) }}
            </option>
          </select></label
        >
        <p>
          {{
            t(
              "麦克风只在点击“开启麦克风”后采集。",
              "Your microphone is captured only after you enable it.",
            )
          }}
        </p>
        <p v-if="!devices.length" class="muted">
          {{
            t(
              "未检测到设备或尚未授权。",
              "No device detected, or access has not been granted.",
            )
          }}
        </p>
      </div>
      <form v-else-if="dialog === 'password'" @submit.prevent="changePassword">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="form.oldPassword"
            type="password"
            required
            autocomplete="current-password" /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="form.newPassword"
            type="password"
            required
            minlength="12"
            maxlength="72"
            autocomplete="new-password"
        /></label>
        <p class="hint">
          {{
            t(
              "12–72字节，含大小写字母和数字。保存后重新登录。",
              "12–72 bytes with uppercase, lowercase and digits. Sign in again after saving.",
            )
          }}
        </p>
        <footer>
          <button class="primary" :disabled="pending">
            {{ t("保存", "Save") }}
          </button>
        </footer>
      </form>
      <div v-else-if="dialog === 'about'" class="about">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <h3>TalkDesk 1.0.0</h3>
        <p>
          {{
            t(
              "知华科技（上海如静知华信息科技有限公司）",
              "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
            )
          }}
        </p>
        <p>
          {{
            t(
              "源码公开、非商业使用。商用需取得书面授权。",
              "Source-available, non-commercial use. Commercial use requires written authorization.",
            )
          }}
        </p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >www.zhuatech.cn</a
        ><template v-if="language === 'zh'"
          ><p>商业授权或深度定制开发请联系知华科技</p>
          <div class="contact-qr">
            <figure>
              <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
              <figcaption>zhuatech</figcaption>
            </figure>
            <figure>
              <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
              <figcaption>zhuatech2</figcaption>
            </figure>
          </div></template
        >
        <p v-else>
          <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a><br /><a
            href="mailto:jack@zhuatech.cn"
            >jack@zhuatech.cn</a
          ><br /><a href="https://wa.me/8617521234993"
            >WhatsApp +86 17521234993</a
          >
        </p>
      </div>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop confirmation">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <footer>
        <button @click="answerConfirmation(false)">
          {{ t("取消", "Cancel") }}</button
        ><button class="primary" @click="answerConfirmation(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </footer>
    </section>
  </div>
</template>
