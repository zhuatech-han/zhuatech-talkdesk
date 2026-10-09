# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Fresh-install HTTP acceptance; creates only clearly labelled TEST data. Does not publish credentials."""

from pathlib import Path
import requests, uuid, json, os

ROOT = Path(__file__).resolve().parents[1]
ENV = dict(
    x.split("=", 1)
    for x in (ROOT / os.environ.get("TALKDESK_TEST_ENV", ".env"))
    .read_text()
    .splitlines()
    if x and not x.startswith("#")
)
BASE = os.environ.get(
    "TALKDESK_TEST_BASE", "http://127.0.0.1:" + ENV.get("WEB_PORT", "8129")
)
checks = 0


def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1


def request(s, path, method="GET", body=None, status=200):
    headers = {}
    if method != "GET":
        c = s.get(BASE + "/api/auth/csrf", timeout=15)
        check(c.status_code == 200, "CSRF bootstrap")
        c = c.json()
        headers[c["header"]] = c["token"]
    r = s.request(method, BASE + "/api" + path, json=body, headers=headers, timeout=20)
    check(
        r.status_code == status,
        f"{method} {path}: expected {status}, got {r.status_code} (response suppressed)",
    )
    return r.json() if "json" in r.headers.get("Content-Type", "") else r.text


def login(name, pw):
    s = requests.Session()
    request(s, "/auth/login", "POST", {"username": name, "password": pw})
    return s


if __name__ == "__main__":
    check(requests.get(BASE + "/health", timeout=15).json()["status"] == "UP", "health")
    check(requests.get(BASE, timeout=15).status_code == 200, "frontend")
    admin = login(ENV.get("ADMIN_USERNAME", "admin"), ENV["ADMIN_PASSWORD"])
    check(
        len(request(admin, "/admin/users")) == 1, "fresh install has only administrator"
    )
    check(
        "passwordHash" not in str(request(admin, "/admin/users")),
        "password hashes private",
    )
    pw = "Aa9" + uuid.uuid4().hex
    suffix = uuid.uuid4().hex[:7]
    org = request(
        admin,
        "/admin/departments",
        "POST",
        {"name": "TEST 外部组织", "zone": "UTC", "enabled": True},
    )["id"]
    users = {}
    for key, label, role, dept in [
        ("alice", "TEST 项目负责人", 3, 1),
        ("bob", "TEST 协作成员", 3, 1),
        ("outsider", "TEST 外部成员", 3, org),
        ("viewer", "TEST 统计查阅", 4, 1),
    ]:
        u = key + "-" + suffix
        row = request(
            admin,
            "/admin/users",
            "POST",
            {
                "username": u,
                "displayName": label,
                "password": pw,
                "roleId": role,
                "departmentId": dept,
                "enabled": True,
            },
        )
        users[key] = {"username": u, "id": row["id"]}
    a = login(users["alice"]["username"], pw)
    b = login(users["bob"]["username"], pw)
    other = login(users["outsider"]["username"], pw)
    viewer = login(users["viewer"]["username"], pw)
    request(requests.Session(), "/rooms", status=401)
    request(a, "/admin/users", status=403)
    request(viewer, "/rooms", status=403)
    room = request(
        a,
        "/rooms",
        "POST",
        {
            "name": "TEST 项目协作",
            "description": "开发验收用房间 / Development acceptance room",
            "category": "TEAM",
            "visibility": "PRIVATE",
            "capacity": 8,
        },
    )
    rid = room["id"]
    path = "/rooms/" + str(rid)
    check(room["joined"] and room["members"] == 1, "owner joins atomically")
    check(room["name"] not in str(request(b, "/rooms")), "private room hidden")
    request(b, path + "/join", "POST", status=409)
    request(b, path + "/messages", status=403)
    invitation = request(a, path + "/invites", "POST", {"maxUses": 2})
    code = invitation["code"]
    check(
        "tokenHash" not in str(request(a, path + "/invites")), "invitation hash private"
    )
    request(other, "/invites/redeem", "POST", {"code": code}, status=409)
    request(b, "/invites/redeem", "POST", {"code": code})
    check(len(request(a, path + "/members")) == 2, "invited member visible")
    payload = {
        "content": "TEST 欢迎进入协作房间。语音加入后默认静音。",
        "nonce": str(uuid.uuid4()),
    }
    m = request(a, path + "/messages", "POST", payload)
    check(
        request(a, path + "/messages", "POST", payload)["id"] == m["id"],
        "message idempotency",
    )
    request(
        b,
        path + "/messages",
        "POST",
        {
            "content": "TEST 已收到，我会在需要发言时开启麦克风。",
            "nonce": str(uuid.uuid4()),
        },
    )
    request(b, path + "/messages/" + str(m["id"]), "DELETE", status=403)
    own = request(
        b,
        path + "/messages",
        "POST",
        {"content": "TEST 应被移除的正文", "nonce": str(uuid.uuid4())},
    )
    request(b, path + "/messages/" + str(own["id"]), "DELETE")
    rows = request(a, path + "/messages")
    check(rows[-1]["removed"] and rows[-1]["content"] == "", "message redaction")
    request(
        a,
        path + "/messages",
        "POST",
        {"content": "x" * 2001, "nonce": str(uuid.uuid4())},
        status=400,
    )
    request(a, path + "/leave", "POST", status=409)
    request(other, path + "/voice", "POST", status=403)
    check(
        request(a, path + "/voice", "POST")["url"] == "/voice",
        "voice lease from authenticated endpoint",
    )
    request(a, path + "/state", "POST", {"status": "LOCKED", "version": 1})
    request(
        b,
        path + "/messages",
        "POST",
        {"content": "blocked", "nonce": str(uuid.uuid4())},
        status=409,
    )
    request(b, path + "/voice", "POST", status=409)
    request(a, path + "/state", "POST", {"status": "OPEN", "version": 1}, status=409)
    request(a, path + "/state", "POST", {"status": "OPEN", "version": 2})
    request(
        a, path + "/members/" + str(users["bob"]["id"]), "PUT", {"status": "BANNED"}
    )
    request(b, path + "/messages", status=403)
    request(b, "/invites/redeem", "POST", {"code": code}, status=409)
    request(a, path + "/members/" + str(users["bob"]["id"]), "PUT", {"status": "LEFT"})
    fresh = request(a, path + "/invites", "POST", {"maxUses": 1})
    request(b, "/invites/redeem", "POST", {"code": fresh["code"]})
    check(len(request(a, path + "/messages")) == 3, "history persisted after rejoin")
    csv = request(admin, "/reports/rooms.csv")
    check(
        "欢迎进入" not in csv and "nonce" not in csv,
        "CSV excludes messages and credentials",
    )
    request(viewer, "/stats")
    request(viewer, "/admin/users", status=403)
    check(
        requests.get(BASE + "/api/voice/authorize", timeout=15).status_code == 404,
        "voice authorizer internal only",
    )
    check(
        requests.post(
            BASE + "/voice/twirp/livekit.RoomService/ListRooms", timeout=15
        ).status_code
        == 404,
        "media administration not exposed",
    )
    # A separate organization-discoverable room for real media tests.
    rtcroom = request(
        a,
        "/rooms",
        "POST",
        {
            "name": "TEST 语音验收",
            "description": "合成音频验收，不采集真实麦克风 / Synthetic audio acceptance",
            "category": "TEAM",
            "visibility": "ORGANIZATION",
            "capacity": 8,
        },
    )
    request(b, "/rooms/" + str(rtcroom["id"]) + "/join", "POST")
    state = {
        "base": BASE,
        "password": pw,
        "users": users,
        "roomId": rid,
        "rtcRoomId": rtcroom["id"],
        "checks": checks,
    }
    f = ROOT / "private-quality-state.json"
    f.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    f.chmod(0o600)
    print(
        json.dumps(
            {
                "passed": checks,
                "roomId": rid,
                "rtcRoomId": rtcroom["id"],
                "state": "private-quality-state.json",
            },
            ensure_ascii=False,
        )
    )
