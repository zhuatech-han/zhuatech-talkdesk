// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 语音租约与持续权限核对；成员禁入、锁房、停用和密码变更阻断重连。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class VoiceService {
  final Store db;
  final ChatService chat;
  final AccessService access;
  final VoiceGateway rtc;
  final Clock clock;

  @org.springframework.beans.factory.annotation.Value("${talkdesk.reconcile-enabled:true}")
  boolean reconcileEnabled;

  public VoiceService(Store d, ChatService c, AccessService a, VoiceGateway r, Clock k) {
    db = d;
    chat = c;
    access = a;
    rtc = r;
    clock = k;
  }

  /** 只为实际有效成员开房；每设备一个身份，不顶替另一个设备。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object join(Long id, String session) {
    var room = db.lock(ChatRoom.class, id);
    chat.writable(id);
    var user = access.current();
    String physical = "talk-" + id + "-" + room.generation;
    rtc.create(physical, room.capacity);
    var lease = new VoiceLease();
    lease.roomId = id;
    lease.accountId = user.id;
    lease.physicalRoom = physical;
    lease.identity = "u-" + user.id + "-" + UUID.randomUUID();
    lease.credential = rtc.credential(user);
    lease.sessionKey = ChatService.hash(session);
    lease.expiresAt = clock.instant().plusSeconds(45);
    db.save(lease);
    access.audit("VOICE_JOIN_AUTHORIZED", id, room.departmentId);
    return Map.of(
        "token",
        rtc.token(lease.identity, user.displayName, physical),
        "url",
        "/voice",
        "identity",
        lease.identity,
        "leaseId",
        lease.id);
  }

  /**
   * 网关每次WebSocket连接/重连重新检查成员状态，不依赖LiveKit旧令牌撤销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  @Transactional(readOnly = true)
  public void authorize(String token) {
    var c = rtc.verify(token);
    var leases = db.query(VoiceLease.class, "from VoiceLease where identity=?1", c.get("sub"));
    if (leases.size() != 1) throw new Problem(403, "VOICE_DENIED");
    var l = leases.getFirst();
    var grant = (Map<?, ?>) c.get("video");
    if (!l.physicalRoom.equals(grant.get("room")) || !valid(l))
      throw new Problem(403, "VOICE_DENIED");
  }

  boolean valid(VoiceLease l) {
    var a = db.get(Account.class, l.accountId);
    var r = db.get(ChatRoom.class, l.roomId);
    return !l.revoked
        && chat.active(a, r)
        && l.credential.equals(rtc.credential(a))
        && l.physicalRoom.equals("talk-" + r.id + "-" + r.generation);
  }

  /** 离开先撤销持久权限，再由巡检可靠移出媒体服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object leave(Long lid) {
    var l = db.get(VoiceLease.class, lid);
    if (!l.accountId.equals(access.current().id)) throw new Problem(403, "FORBIDDEN");
    l.revoked = true;
    return Map.of("ok", true);
  }

  /** 退出当前会话撤销该会话下所有语音连接，不影响其他设备会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void logout(String session) {
    for (var l :
        db.query(
            VoiceLease.class, "from VoiceLease where sessionKey=?1", ChatService.hash(session)))
      l.revoked = true;
  }

  /** 单实例两秒巡检；失败保留租约下次重试，不把失败宣布为已移出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(fixedDelay = 2000)
  public void reconcile() {
    if (!reconcileEnabled) return;
    for (var l : db.all(VoiceLease.class)) {
      if (!valid(l)) {
        try {
          rtc.remove(l.physicalRoom, l.identity);
          db.delete(l);
        } catch (Problem ignored) {
          /*服务不可用时不删撤销记录，网关继续拒绝*/
        }
      } else if (l.expiresAt.isBefore(clock.instant().minusSeconds(120))) {
        try {
          var res = rtc.call("ListParticipants", l.physicalRoom, Map.of("room", l.physicalRoom));
          var ps = (List<?>) res.get("participants");
          boolean found =
              ps != null
                  && ps.stream()
                      .anyMatch(
                          x -> x instanceof Map<?, ?> m && l.identity.equals(m.get("identity")));
          if (!found) db.delete(l);
        } catch (Problem ignored) {
          /*保留以便恢复后核对*/
        }
      }
    }
  }
}
