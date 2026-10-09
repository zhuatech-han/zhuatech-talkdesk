# TalkDesk 接口与权限

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

所有业务请求同源 /api，写请求携带 GET /api/auth/csrf 返回的请求头及令牌。登录由服务器建立 HttpOnly 会话，不在浏览器长期存储密码或语音票据。

| 接口 | 行为与约束 |
|---|---|
| /auth/login、/me、/logout、/password | 登录、当前权限、会话退出、验证旧密码后修改 |
| GET/POST /rooms、PUT /rooms/{id} | 发现可见房间、创建、按 version 完整编辑 |
| POST /rooms/{id}/join、/leave、/owner | 组织房加入、成员离开、转让房主 |
| POST /rooms/{id}/state | OPEN / LOCKED / ARCHIVED；version 必须当前；归档不可恢复 |
| GET /rooms/{id}/members | 有效成员读取；管理者额外查看禁入/离开记录 |
| PUT /rooms/{id}/members/{uid} | BANNED 禁入，LEFT 解除禁入但不自动加入；不可禁入当前房主 |
| GET/POST /rooms/{id}/invites | 使用量列表、生成邀请；代码原文仅创建响应返回 |
| POST /rooms/{id}/invites/{iid}/revoke、/invites/redeem | 撤销、兑换；按房间行锁限制容量及使用量 |
| GET/POST /rooms/{id}/messages | 最近50条，before 为上一页最早ID；POST含 content 与 nonce |
| DELETE /rooms/{id}/messages/{mid} | 本人或房间管理者移除正文 |
| POST /rooms/{id}/voice | 短期音频加入票据，返回相对 /voice 地址、identity、leaseId |
| POST /voice/{leaseId}/leave | 只撤销本人的语音租约 |
| /admin/{kind}、/admin/options | 账号、角色、权限、组织、字典、菜单、参数；管理要求 ALL 范围 |
| GET /stats、/reports/rooms.csv、/audit | 组织范围统计、无正文CSV、无凭据操作审计 |

/voice/rtc WebSocket 必须经过 Nginx 内部鉴权子请求；/api/voice/authorize 对外返回404。授权支持 SDK 的 access_token 参数及 Bearer 请求头。服务端验证签名、签发者、过期时间、加入授权、租约、房间代际、账号密码指纹、当前角色/组织及有效成员关系。LiveKit 的 Twirp 管理入口、后台端口及原生信令端口不对外开放；不得绕过网关单独暴露7880，否则成员撤销的约束不再完整。

知华后台仅为 microphone 来源签发发布权限，不允许视频、数据通道、录制、电话或房间管理权限。文字聊天走持久化业务接口，不依赖媒体临时数据消息。

401 会话/票据失效，403 权限不足，400 字段格式错误，409 状态/版本/容量冲突，503 语音服务不可用。接口不回显密码、BCrypt、语音密钥、邀请摘要或原始异常。成功响应不表示购买者已经取得法定运营许可。
