# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Transmit a labelled test tone for browser playback acceptance. No microphone is used."""

import asyncio, importlib.util
from pathlib import Path

p = Path(__file__).with_name("verify-media.py")
spec = importlib.util.spec_from_file_location("media_fixture", p)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


async def main():
    peer = module.Peer("bob", 660)
    try:
        await peer.connect()
        print(
            "TEST audio sender connected; transmitting synthetic 660 Hz for 45 seconds.",
            flush=True,
        )
        await peer.tone(45)
    finally:
        await peer.close()


if __name__ == "__main__":
    asyncio.run(main())
    import gc

    gc.collect()
