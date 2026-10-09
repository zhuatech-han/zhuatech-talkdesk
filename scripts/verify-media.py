# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Two independent RTC peers exchange distinct synthesized tones through the actual SFU and access gateway.
No microphone is accessed. Does not replace real-device testing or Internet load testing.
"""

import asyncio, json, logging, time, os
from pathlib import Path
import numpy as np
from livekit import rtc
from quality import ROOT, login, request, check

logging.getLogger("livekit").setLevel(logging.CRITICAL)
STATE = json.loads((ROOT / "private-quality-state.json").read_text())


class Peer:
    def __init__(self, key, frequency):
        self.key = key
        self.frequency = frequency
        self.room = rtc.Room()
        self.received = {}
        self.tasks = []
        self.disconnected = asyncio.Event()
        self.source = rtc.AudioSource(48000, 1)

        @self.room.on("track_subscribed")
        def subscribed(track, publication, participant):
            if track.kind == rtc.TrackKind.KIND_AUDIO:
                self.tasks.append(
                    asyncio.create_task(self.consume(track, participant.identity))
                )

        @self.room.on("disconnected")
        def disconnected(reason):
            self.disconnected.set()

    async def connect(self):
        self.session = login(STATE["users"][self.key]["username"], STATE["password"])
        self.lease = request(
            self.session, "/rooms/" + str(STATE["rtcRoomId"]) + "/voice", "POST"
        )
        print("Connecting labelled TEST RTC peer " + self.key, flush=True)
        await self.room.connect(
            STATE["base"].replace("http:", "ws:") + "/voice",
            self.lease["token"],
            rtc.RoomOptions(
                connect_timeout=12,
                single_peer_connection=False,
                rtc_config=(
                    rtc.RtcConfiguration(
                        ice_transport_type=rtc.IceTransportType.TRANSPORT_RELAY
                    )
                    if os.environ.get("TALKDESK_RELAY_TEST") == "1"
                    else None
                ),
            ),
        )
        print("TEST RTC peer connected: " + self.key, flush=True)
        self.track = rtc.LocalAudioTrack.create_audio_track(
            "TEST synthetic tone", self.source
        )
        await self.room.local_participant.publish_track(
            self.track,
            rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE),
        )

    async def consume(self, track, identity):
        stream = rtc.AudioStream(track, sample_rate=48000, num_channels=1)
        try:
            async for event in stream:
                samples = np.frombuffer(event.frame.data, dtype=np.int16).astype(
                    np.float64
                )
                if np.sqrt(np.mean(samples * samples)) > 400:
                    spectrum = np.abs(np.fft.rfft(samples * np.hanning(len(samples))))
                    freq = float(
                        np.fft.rfftfreq(len(samples), 1 / 48000)[
                            int(np.argmax(spectrum))
                        ]
                    )
                    values = self.received.setdefault(identity, [])
                    values.append(freq)
        finally:
            await stream.aclose()

    async def tone(self, seconds=6):
        for k in range(int(seconds * 50)):
            points = np.arange(960) + k * 960
            samples = (
                np.sin(2 * np.pi * self.frequency * points / 48000) * 6000
            ).astype(np.int16)
            await self.source.capture_frame(
                rtc.AudioFrame(samples.tobytes(), 48000, 1, 960)
            )
        await self.source.wait_for_playout()

    async def close(self):
        await self.room.disconnect()
        for t in self.tasks:
            t.cancel()
        await asyncio.gather(*self.tasks, return_exceptions=True)
        await self.source.aclose()


async def main():
    a, b = Peer("alice", 440), Peer("bob", 880)
    try:
        await a.connect()
        await b.connect()
        await asyncio.sleep(1)
        await asyncio.gather(a.tone(), b.tone())
        await asyncio.sleep(1)
        result = {"forcedRelay": os.environ.get("TALKDESK_RELAY_TEST") == "1"}
        for receiver, sender in [(a, b), (b, a)]:
            samples = receiver.received.get(sender.lease["identity"], [])
            check(len(samples) >= 30, "real received audio frame count")
            median = float(np.median(samples))
            check(
                abs(median - sender.frequency) < 60,
                "received audio has remote tone frequency",
            )
            result[receiver.key] = {
                "remoteAudioFrames": len(samples),
                "expectedHz": sender.frequency,
                "measuredMedianHz": median,
            }
        # Active media must end after persistent ban; old, still signed JWT cannot reconnect.
        request(
            a.session,
            "/rooms/"
            + str(STATE["rtcRoomId"])
            + "/members/"
            + str(STATE["users"]["bob"]["id"]),
            "PUT",
            {"status": "BANNED"},
        )
        await asyncio.wait_for(b.disconnected.wait(), timeout=10)
        probe = rtc.Room()
        denied = False
        try:
            await probe.connect(
                STATE["base"].replace("http:", "ws:") + "/voice",
                b.lease["token"],
                rtc.RoomOptions(connect_timeout=5),
            )
        except Exception:
            denied = True
        finally:
            await probe.disconnect()
        check(denied, "banned old JWT cannot reconnect through gateway")
        result["banDisconnectAndOldTokenRejoinDenied"] = True
        request(
            a.session,
            "/rooms/"
            + str(STATE["rtcRoomId"])
            + "/members/"
            + str(STATE["users"]["bob"]["id"]),
            "PUT",
            {"status": "LEFT"},
        )
        request(b.session, "/rooms/" + str(STATE["rtcRoomId"]) + "/join", "POST")
        f = ROOT / (
            "private-relay-result.json"
            if os.environ.get("TALKDESK_RELAY_TEST") == "1"
            else "private-media-result.json"
        )
        f.write_text(json.dumps(result, indent=2))
        f.chmod(0o600)
        print(json.dumps(result))
    finally:
        await a.close()
        await b.close()


if __name__ == "__main__":
    asyncio.run(main())
    import gc

    gc.collect()
