# TalkDesk 部署与数据维护

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 本机学习部署

需要 Docker Engine/Desktop、Compose V2、Python3.10+；直接源码开发需要 JDK21、Maven3.9+、Node24.19.0+。至少4GB可用内存，首次构建需能访问 Maven、npm、Docker 官方仓库。

```sh
python3 scripts/init-env.py
docker compose -p talkdesk config --quiet
docker compose -p talkdesk up -d --build --wait --wait-timeout 300
```

访问 http://127.0.0.1:8129/，健康检查 http://127.0.0.1:8129/health。管理员默认用户名 admin，密码是私有 .env 的 ADMIN_PASSWORD。生成脚本不输出密码、不覆盖已有 .env；配置密钥不能提交 Git。私有 runtime/livekit.yaml 根据 .env 生成，必须随部署保留且不能上传。

MySQL 使用专用卷和 Flyway V1身份 / V2聊天；JPA 只校验结构，不自动改表。空库仅初始化一个管理员及角色、菜单、组织和字典，没有共享演示密码、测试用户或聊天。升级前备份，添加新的版本迁移，不改已应用迁移的校验和。已有库启动不重置密码。

WEB_PORT 可改 Web 端口；RTC_TCP_PORT / RTC_UDP_PORT / TURN_UDP_PORT 与 TURN_RELAY_START / END 可改媒体端口及中继范围，改完保存自定义配置，再运行 init-env.py --render 重新渲染配置。主机端口与容器声明端口一致，不将映射端口随意错位。默认 Web 和媒体仅绑定回环；回环配置主要用于同机学习，不能当成其他设备可达的地址。

## 客户自己的局域网或互联网部署

1. 使用客户自己的主机、域名、合法使用环境及书面商业授权；源码交付不等于网站/APP备案或通信平台运营许可。
2. RTC_NODE_IP 设为客户端实际可达的服务器IPv4地址；RTC_BIND_ADDRESS 设为服务器确实拥有的监听地址。NAT主机须正确映射媒体端口。域名不是 RTC_NODE_IP 的替代值。
3. Web 使用客户现有可信 HTTPS 反向代理，支持 WebSocket，Cookie Secure=true。手机及非localhost页面采集麦克风要求安全上下文；不要绕过证书警告。只将 Web 域名代理到前端8080/WEB_PORT，不直连 LiveKit7880或后端8080。
4. 防火墙按需开放 RTC_TCP_PORT、RTC_UDP_PORT、TURN_UDP_PORT 和精确中继UDP范围。内置 TURN/UDP 使用服务器动态会话凭据。TLS-only公司网络还需要客户配置TURN/TLS证书与相应端口，当前生成脚本不自动申请证书或配置TURN/TLS。
5. TURN 默认拒绝任意私有网目标；如SFU处于Docker私网，按实际部署将允许目标限定为该SFU的精确地址，不能开放整个内网。本机回环调试可在启动后运行 `python3 scripts/configure-local-relay.py --project talkdesk`；容器重建改变地址时重新核对。实际服务地址仍须可达，允许范围不解决错误地址或端口映射。
6. 根据并发数设置足够的中继端口范围和CPU/带宽。当前学习版未做高并发、跨运营商、跨国或移动网络性能验收，不提供可直接对公众运营的保证。

参考 [LiveKit部署](https://docs.livekit.io/transport/self-hosting/deployment/)、[端口与防火墙](https://docs.livekit.io/transport/self-hosting/ports-firewall/)。不得为了访问方便关闭鉴权网关、暴露密钥或数据库。

## 配置

| 名称 | 用途 |
|---|---|
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | 独立强数据库凭据 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 首次管理员；已有库不重置 |
| LIVEKIT_API_KEY / LIVEKIT_API_SECRET | 自部署媒体签名，secret至少32字符，非云API付费凭据 |
| WEB_PORT / BIND_ADDRESS | Web监听，默认8129 / 回环 |
| RTC_NODE_IP / RTC_BIND_ADDRESS | 客户端可达媒体地址 / 本机绑定地址 |
| RTC_TCP_PORT / RTC_UDP_PORT | ICE TCP/UDP，默认7891 / 7892 |
| TURN_UDP_PORT / TURN_RELAY_START / TURN_RELAY_END | TURN入口7893及中继UDP范围7900–7910；并发增加需扩展 |
| LIVEKIT_CONFIG_PATH | runtime内独立私有配置文件 |
| COOKIE_SECURE | HTTPS环境设true |

## 备份与恢复

备份包含私有消息、邀请摘要及密码散列，属于敏感文件，不可上传仓库、发送公开链接或用于README截图。只恢复可信本机备份；SQL导入不是安全沙箱。

```sh
python3 scripts/backup.py --project talkdesk --output private-backups/talkdesk.zip
python3 scripts/init-env.py --env-file .env.restore --web-port 8130 --tcp-port 7897 --udp-port 7898 --turn-port 7899 --runtime-file ./runtime/livekit-restore.yaml
```

恢复前为新环境配置独立、不冲突的 TURN_RELAY_START/END（例如8000–8010），重新以 --env-file .env.restore --render 运行 init-env.py；恢复脚本拒绝已有项目、卷和网络。然后：

```sh
python3 scripts/restore.py private-backups/talkdesk.zip --project talkdesk-restore --env-file .env.restore
```

恢复的管理员密码来自原数据库，不是新 .env.restore 生成的初始化密码。媒体密钥可以独立生成；旧租约不应用来继续旧通话。备份过程暂停后台业务写入，结束后恢复后台；实际维护窗口应提前结束语音会话。恢复后核对消息、成员状态、授权及CSV，再由客户切换正式入口。不要对已有生产数据卷执行 down -v。
