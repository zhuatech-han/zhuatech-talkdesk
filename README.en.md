[中文](README.md) | [English](README.en.md)

# TalkDesk Self-hosted Voice Chat and Room Management · WebRTC / Java 21 / Spring Boot / Vue 3

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Website](https://www.zhuatech.cn/)

TalkDesk provides source code for teams, gaming or interest communities, and applications that need their own account-controlled voice rooms. Members join through a browser, messages persist in MySQL, and audio is transmitted by a customer-deployed LiveKit server. ZhiHua does not operate a public communications platform or host user voice accounts.

**Source-available learning edition / non-commercial edition.** Personal learning, technical research and non-commercial exchange only. Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. This is not an OSI open-source license allowing free commercial use. See [LICENSE](LICENSE). Commercial source licensing does not replace third-party licenses or confer website, app or communications operating authorizations.

## Workflow and implemented features

Administrators create organizations, roles and accounts; a fresh installation contains only the administrator. Members create private or organization-visible rooms with category, description and capacity. Eligible organization members can join discoverable rooms; private rooms require an expiring, usage-limited invitation and an existing login account.

| Interface | Implemented capabilities |
|---|---|
| Member | Room search/state filters/sorting, invitation redemption, persistent messages, deduplication, 50-message cursor history, own-message removal, group voice, mute, input-device and connection-mode selection, actual receive counters, Chinese/English UI and responsive layout |
| Room owner/manager | Version-checked editing, category/visibility/capacity, lock/reopen/archive, limited invitations and revocation, ban/unban, ownership transfer |
| Administration | BCrypt accounts, enable/disable and password reset, roles/permissions, organizations/time zones, category dictionary, fixed menu names/order/enabled state, parameters, search/pagination and last-administrator protection |
| Statistics and audit | Scoped metrics, room CSV without message contents, identity/room/member audit without passwords or invitation secrets |
| Deployment/security | MySQL, Flyway, sessions/CSRF, per-request authorization, voice access gateway, short-lived join tokens, continuing revocation checks, self-hosted SFU/TURN, private configuration generation, backup and isolated restore |

Joining voice starts muted; microphone capture begins only after an explicit enable action. Playback restrictions and reconnection status are shown. Members can leave after transferring ownership if necessary. Locking preserves member read access but stops sending and voice; archiving cannot be reversed. Report-only accounts cannot read private conversations.

The server enforces organization scope, membership, room state, capacity and invitation limits. Voice grants permit microphone publication only, without video, recording, data publication or media administration. Bans, locking, account disable/password changes and session logout block old-token reconnection. Existing media is removed by a two-second reconciliation cycle; outages retain revocations for retry rather than guaranteeing a fixed removal latency.

## Actual running screens

These are real running pages with labelled TEST acceptance data, not customer data, customer cases or fictional templates. Voice screenshots use synthesized audio without accessing a physical microphone. Fresh installation does not include these test records.

### Sign-in

Private account sign-in without shared passwords.

![Sign-in](docs/screenshots/01-login.jpg)

### Member home

Search, filter and select rooms or redeem an invitation.

![Member home](docs/screenshots/07-home.jpg)

### Messages and members

Persisted messages, removal markers and membership management.

![Messages and members](docs/screenshots/02-chat.jpg)

### Room settings

Name, category, capacity and visibility.

![Room settings](docs/screenshots/03-room-settings.jpg)

### Invitations

Expiry, usage limits and revocation without showing invitation secrets.

![Invitations](docs/screenshots/04-invitations.jpg)

### Live voice

Browser receiving a synthesized test tone while muted; this is not a customer call.

![Live voice](docs/screenshots/05-voice.jpg)

### Audio devices

Input devices, connection mode and actual received packet counters.

![Audio devices](docs/screenshots/06-audio-devices.jpg)

### Accounts

Administrator-managed accounts, roles, organizations and enabled state.

![Accounts](docs/screenshots/08-accounts.jpg)

### Roles and permissions

Permissions for members, organization moderators and report viewers.

![Roles and permissions](docs/screenshots/09-roles.jpg)

### Organizations and settings

Organizations, time zones, dictionaries, parameters and menus.

![Organizations and settings](docs/screenshots/10-settings.jpg)

### Statistics

Actual scoped metrics and export without message contents.

![Statistics](docs/screenshots/11-statistics.jpg)

### Audit trail

Account, room and member audit without passwords or message contents.

![Audit trail](docs/screenshots/12-audit.jpg)

### English interface

Language switching does not translate or rewrite message contents.

![English interface](docs/screenshots/14-english.jpg)

### Mobile layout

Responsive browser layout at 390×844; this is not physical-phone call validation.

![Mobile layout](docs/screenshots/15-mobile.jpg)

## Architecture and layout

Java 21, Spring Boot 4.0.7, Security, JPA/Hibernate and Flyway; Vue 3.5.43, Vite 8.1.5, LiveKit Client 2.22.3 and Lucide; MySQL 8.4, LiveKit Server 1.13.9, Nginx and Docker Compose. Browser requests use same-origin /api and /voice. WebSocket joins pass through business authorization; raw media management ports are private. Room row locks serialize capacity and invitation consumption. Voice uses the verified dual-PeerConnection mode. WebRTC transport encryption is not a claim of server-blind end-to-end encryption.

```text
backend/src/main/java/cn/zhuatech/talkdesk/ # auth, administration, chat, voice gateway
backend/src/main/resources/db/migration/  # V1 identity / V2 chat
backend/src/test/java/                    # HTTP business, concurrency and token tests
frontend/src/                            # member, room/voice and administration UI
frontend/public/brand/                   # original brand assets
docs/                                   # user, deployment, API and license notes
scripts/                                # configuration, private QA, backup/restore
compose.yaml                            # MySQL, backend, LiveKit, frontend gateway
.env.example                            # configuration names without credentials
```

## Requirements, installation and database initialization

Docker Engine/Desktop, Compose V2, Python 3.10+ and at least 4 GB available memory. Direct source development requires JDK 21, Maven 3.9+ and Node.js 24.19.0+. Initial builds need access to official Maven/npm/Docker registries. No existing database or paid voice cloud account is required.

```sh
python3 scripts/init-env.py
docker compose -p talkdesk config --quiet
docker compose -p talkdesk up -d --build --wait --wait-timeout 300
```

Open [http://127.0.0.1:8129/](http://127.0.0.1:8129/); health is [http://127.0.0.1:8129/health](http://127.0.0.1:8129/health). Initial username defaults to admin. Read ADMIN_PASSWORD from the private generated `.env`; there is no shared password in the repository. Existing `.env` is not overwritten, and existing database accounts are not reset. Initial data includes roles, menus, one organization, categories and parameters—not conversations or test accounts.

Flyway applies V1 identity and V2 chat. JPA validates rather than modifies the schema. Add new versioned migrations for upgrades, retain existing checksums and back up first. Database credentials, administrator initialization and media keys come from environment variables. The generated `runtime/livekit.yaml` and `.env` contain private values and must never be committed.

## Configuration and deployment

| Variables | Purpose |
|---|---|
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | Independent strong database credentials |
| ADMIN_USERNAME / ADMIN_PASSWORD | First empty-database administrator only |
| LIVEKIT_API_KEY / LIVEKIT_API_SECRET | Self-hosted media signing; secret must have 32+ characters; not a paid cloud API |
| WEB_PORT / BIND_ADDRESS | Web listener, default 8129 / loopback |
| RTC_NODE_IP / RTC_BIND_ADDRESS | Client-reachable media address / local listener address |
| RTC_TCP_PORT / RTC_UDP_PORT | ICE TCP/UDP, default 7891/7892 |
| TURN_UDP_PORT / TURN_RELAY_START / TURN_RELAY_END | TURN 7893 and relay UDP 7900–7910; expand for concurrency |
| LIVEKIT_CONFIG_PATH | Independent private configuration inside runtime |
| COOKIE_SECURE | Set true behind trusted HTTPS |

Port variables support parallel projects without stopping unrelated services. Existing runtime configuration is retained. After changing media settings, back up custom settings and explicitly re-render with --render. Loopback is for same-machine testing; other devices require reachable addresses and trusted HTTPS. Microphone capture on phones/non-localhost pages requires a secure context. Do not bypass certificate checks or expose backend 8080, LiveKit 7880, Twirp administration or MySQL.

For direct development, run `mvn spring-boot:run` in backend with DATABASE_URL/USER/PASSWORD, ADMIN_PASSWORD, LIVEKIT_API_KEY/SECRET and LIVEKIT_INTERNAL_URL explicitly configured; run your media service as well. Frontend `npm ci && npm run dev` proxies business endpoints to localhost:8080. Use the Compose authorization gateway for complete voice testing instead of an unauthenticated development proxy.

Customer deployment must configure its own host/domain, HTTPS reverse proxy with WebSocket support, NAT and necessary TCP/UDP/relay ports. Built-in TURN/UDP uses dynamic session credentials and denies arbitrary private-network targets; allow only your exact SFU address when needed. The local-only configure-local-relay.py helper can update an already-started loopback-bound test instance; reachable media addressing remains necessary. TURN/TLS certificates and ports are a deployer configuration task, not an automatically completed integration. No public operating authorizations are bundled with source licensing.

See [deployment instructions](docs/DEPLOYMENT.md), [LiveKit deployment](https://docs.livekit.io/transport/self-hosting/deployment/) and [firewall reference](https://docs.livekit.io/transport/self-hosting/ports-firewall/).

## Backup, restore and upgrades

Backups contain private messages, invitation digests and password hashes. Keep them private and restore only trusted local backups; SQL import is not sandboxed. Backup pauses backend writes and resumes afterward; schedule maintenance and end voice calls beforehand.

```sh
python3 scripts/backup.py --project talkdesk --output private-backups/talkdesk.zip
python3 scripts/init-env.py --env-file .env.restore --web-port 8130 --tcp-port 7897 --udp-port 7898 --turn-port 7899 --runtime-file ./runtime/livekit-restore.yaml
```

Set a non-conflicting relay range such as 8000–8010 in the new `.env.restore`, re-render with `python3 scripts/init-env.py --env-file .env.restore --render`, then:

```sh
python3 scripts/restore.py private-backups/talkdesk.zip --project talkdesk-restore --env-file .env.restore
```

Restore refuses existing projects, volumes or networks. Restored account passwords come from the original database, not the new initialization password. Use independent media keys, verify messages/membership/states/CSV and create new voice connections. Never run down -v against an existing production data volume.

## Tests and validation boundaries

```sh
# backend directory
mvn spotless:check test
# frontend directory
npm ci
npm run format:check
npm run lint
npm test
npm run build
# repository root, disposable empty instance only
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements-quality.txt
.venv/bin/python scripts/quality.py
.venv/bin/python scripts/verify-media.py
```

54 backend tests: 42 identity/migration/HTTP/permission/concurrency tests plus 12 token/internal media-protocol tests. Eight frontend request/session tests. Media external calls are substituted only in isolated backend tests; acceptance separately uses actual MySQL/LiveKit and two independent RTC peers exchanging synthesized audio, including ban disconnection and old-token rejection. `TALKDESK_RELAY_TEST=1` forces relay testing after configuring reachable server addresses and relay ports.

quality.py requires a fresh disposable database and creates labelled TEST records plus ignored private-quality-state.json. Never run it on customer production data. verify-browser-audio.py transmits 45 seconds of synthesized audio for browser receive checks. verify-persistence.py compares persisted records across restart/restore. These do not replace physical microphone/phone, public-network or load testing.

Four configuration protection tests (`python3 scripts/test-config.py`) verify private-file permissions, preservation of existing credentials/custom configuration and rejection of invalid node addresses.

## Known limitations and troubleshooting

- Single backend/SFU learning deployment; no attachments, direct-message/friend system, video, screen sharing, recording, transcription, AI chat, telephone dialling, stranger matching or gifts.
- New-room member limit defaults to 16, configurable up to 32. This is a software bound, not validated 32-person audio capacity or a production SLA. Directory reads are bounded at 10000 rows; messages paginate by 50. Text refresh uses 3-second polling, without a completed clustered push gateway.
- MySQL stores account hashes, memberships, invitation digests and message contents. Audio is not recorded. TLS/SRTP is not application-level end-to-end encryption; deployers remain responsible for server data security and lawful processing.
- Local/private-interface synthesized audio, browser reception and responsive layout have been exercised. Physical phone hardware, multi-carrier Internet, sustained concurrency, international networks and production customer usage have not. No claim of production operating readiness is made.
- If text works but voice fails, check the media service, reachable address, ports and TURN. Loopback is not an external address. If microphone access is denied/unavailable, listening can continue; check devices and site permissions. Rejoin after service reconstruction. Never disable authentication or certificate verification.
- For unknown write outcomes, refresh before resubmitting. Reauthenticate expired sessions and refresh before resolving version conflicts. See [user guide](docs/USER_GUIDE.md) and [API reference](docs/API.md).

## License, contribution and contact

Personal learning, technical research and non-commercial exchange only. Commercial delivery, paid deployment, SaaS operation, source resale, commercial training, packaged commercial solutions and paid third-party services require prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Preserve attribution, website, copyright, license and contact information. [LICENSE](LICENSE) covers ZhiHua-owned code; third parties retain their licenses and copyrights, including Apache-2.0 LiveKit components. See [third-party notes](docs/THIRD_PARTY.md).

ZhiHua provides commercial licensing, private deployment, custom development, enterprise digitalization, AI transformation services, implementation, systems integration, FDE and OPC technical support. Software is provided as-is under LICENSE; deployers must determine applicable local laws, filings and operating permits. Purchasing code does not confer those qualifications.

Read the non-commercial license before contributing. Submit reproducible issues and tests without customer messages, invitation codes, passwords, tokens, keys or private backups. General feedback can use repository Issues or the website; report exploitable vulnerabilities privately via the website or contacts below.

- Website: [www.zhuatech.cn](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
