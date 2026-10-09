# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Compare persisted rooms, members, messages and CSV after restart or isolated restore. Private state is never published."""

import argparse, json, hashlib
from quality import ROOT, login, request, check

p = argparse.ArgumentParser()
p.add_argument("--capture", action="store_true")
p.add_argument("--snapshot", default="private-persistence-state.json")
a = p.parse_args()
s = json.loads((ROOT / "private-quality-state.json").read_text())
session = login(s["users"]["alice"]["username"], s["password"])
rooms = request(session, "/rooms")
out = {"rooms": rooms, "details": {}}
for r in rooms:
    if r["joined"]:
        key = "/rooms/" + str(r["id"])
        out["details"][str(r["id"])] = {
            "members": request(session, key + "/members"),
            "messages": request(session, key + "/messages"),
        }
f = ROOT / a.snapshot
if a.capture:
    f.write_text(json.dumps(out, ensure_ascii=False, indent=2))
    f.chmod(0o600)
    print("Private persistence snapshot captured; credentials excluded.")
else:
    old = json.loads(f.read_text())
    check(old == out, "identical room/membership/message snapshot")
    print("Persisted rooms, members, message bodies and states match snapshot.")
