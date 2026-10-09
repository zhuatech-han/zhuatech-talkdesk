// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 持久聊天、邀请、成员与权限闭环；房间行锁串行化容量及状态变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ChatService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  public ChatService(Store d, AccessService a, AdminService s, Clock c) {
    db = d;
    access = a;
    admin = s;
    clock = c;
  }

  /** 邀请原文不入库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(String x) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(x.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  RoomMember membership(Long room, Long user) {
    return db
        .query(RoomMember.class, "from RoomMember where roomId=?1 and accountId=?2", room, user)
        .stream()
        .findFirst()
        .orElse(null);
  }

  boolean manager(ChatRoom r) {
    return Objects.equals(r.ownerId, access.current().id)
        || (access.has("moderate") && access.department(r.departmentId));
  }

  boolean eligible(Account a, ChatRoom r) {
    var role = db.get(AccessRole.class, a.roleId);
    return a.enabled
        && db.get(Department.class, a.departmentId).enabled
        && db.get(Department.class, r.departmentId).enabled
        && role.permissions.contains("chat")
        && (role.scope.equals("ALL") || Objects.equals(a.departmentId, r.departmentId));
  }

  /** 语音入口与持续撤销检查使用同一实际成员/组织规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean active(Account a, ChatRoom r) {
    var m = membership(r.id, a.id);
    return r.status.equals("OPEN") && eligible(a, r) && m != null && m.status.equals("ACTIVE");
  }

  ChatRoom readable(Long id) {
    access.require("chat");
    var r = db.get(ChatRoom.class, id);
    var m = membership(id, access.current().id);
    if (!access.department(r.departmentId)
        || !db.get(Department.class, r.departmentId).enabled
        || m == null
        || !m.status.equals("ACTIVE")) throw new Problem(403, "ROOM_FORBIDDEN");
    return r;
  }

  ChatRoom writable(Long id) {
    var r = readable(id);
    Rules.check(r.status.equals("OPEN"), "ROOM_CLOSED");
    return r;
  }

  ChatRoom managed(Long id) {
    access.require("rooms");
    var r = db.lock(ChatRoom.class, id);
    if (!access.department(r.departmentId) || !manager(r)) throw new Problem(403, "FORBIDDEN");
    return r;
  }

  Map<String, Object> view(ChatRoom r) {
    var m = membership(r.id, access.current().id);
    return Map.ofEntries(
        Map.entry("id", r.id),
        Map.entry("name", r.name),
        Map.entry("description", r.description),
        Map.entry("category", r.category),
        Map.entry("visibility", r.visibility),
        Map.entry("status", r.status),
        Map.entry("capacity", r.capacity),
        Map.entry("version", r.version),
        Map.entry("departmentId", r.departmentId),
        Map.entry("ownerId", r.ownerId),
        Map.entry(
            "members",
            db.query(RoomMember.class, "from RoomMember where roomId=?1 and status='ACTIVE'", r.id)
                .size()),
        Map.entry("joined", m != null && m.status.equals("ACTIVE")),
        Map.entry("canManage", manager(r)));
  }

  /** 只列本人加入或所在组织可发现的房间，不泄露私有房间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> rooms() {
    access.require("chat");
    return db.all(ChatRoom.class).stream()
        .filter(
            r -> {
              var m = membership(r.id, access.current().id);
              return access.department(r.departmentId)
                  && db.get(Department.class, r.departmentId).enabled
                  && (m == null || !m.status.equals("BANNED"))
                  && ((m != null && m.status.equals("ACTIVE"))
                      || (r.visibility.equals("ORGANIZATION") && r.status.equals("OPEN")));
            })
        .map(this::view)
        .toList();
  }

  /** 新建房间和房主成员关系必须一起提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object create(Map<String, Object> b) {
    access.require("rooms");
    access.require("chat");
    admin.lock();
    var r = new ChatRoom();
    r.departmentId = access.current().departmentId;
    r.ownerId = access.current().id;
    r.createdAt = clock.instant();
    fields(r, b);
    db.save(r);
    add(r, access.current());
    access.audit("ROOM_CREATED", r.id, r.departmentId);
    return view(r);
  }

  void fields(ChatRoom r, Map<String, Object> b) {
    r.name = Rules.text(b.get("name"), 120, true);
    r.description = Rules.paragraph(b.get("description"), 1000, false);
    r.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where type='CATEGORY' and code=?1 and enabled=true",
                    r.category)
                .size()
            == 1,
        "CATEGORY_DISABLED");
    r.visibility = Rules.text(b.get("visibility"), 20, true);
    Rules.check(Set.of("PRIVATE", "ORGANIZATION").contains(r.visibility), "INVALID_INPUT");
    r.capacity = Rules.integer(b.get("capacity"), 1, admin.setting("room_limit"));
    Rules.check(
        db.query(
                    RoomMember.class,
                    "from RoomMember where roomId=?1 and status='ACTIVE'",
                    r.id == null ? 0L : r.id)
                .size()
            <= r.capacity,
        "ROOM_FULL");
  }

  /** 编辑采用版本号；归档后不可重开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object update(Long id, Map<String, Object> b) {
    var r = managed(id);
    Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    Rules.check(!r.status.equals("ARCHIVED"), "ROOM_ARCHIVED");
    fields(r, b);
    r.version++;
    access.audit("ROOM_UPDATED", id, r.departmentId);
    return view(r);
  }

  /** 锁定/重开/归档以代际区分语音房；新代际不接受旧票据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object state(Long id, Map<String, Object> b) {
    var r = managed(id);
    Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    String target = Rules.text(b.get("status"), 20, true);
    Rules.check(
        !r.status.equals("ARCHIVED")
            && Set.of("OPEN", "LOCKED", "ARCHIVED").contains(target)
            && !target.equals(r.status),
        "ROOM_STATE");
    r.status = target;
    r.generation++;
    r.version++;
    access.audit("ROOM_" + target, id, r.departmentId);
    return view(r);
  }

  void add(ChatRoom r, Account a) {
    Rules.check(eligible(a, r), "ROOM_FORBIDDEN");
    var m = membership(r.id, a.id);
    Rules.check(m == null || !m.status.equals("BANNED"), "ROOM_BANNED");
    if (m != null && m.status.equals("ACTIVE")) return;
    Rules.check(
        db.query(RoomMember.class, "from RoomMember where roomId=?1 and status='ACTIVE'", r.id)
                .size()
            < r.capacity,
        "ROOM_FULL");
    if (m == null) {
      m = new RoomMember();
      m.roomId = r.id;
      m.accountId = a.id;
      m.joinedAt = clock.instant();
      db.save(m);
    }
    m.status = "ACTIVE";
    m.joinedAt = clock.instant();
  }

  /** 组织房可直接加入；私有房须使用有效邀请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object join(Long id) {
    access.require("chat");
    var r = db.lock(ChatRoom.class, id);
    Rules.check(r.visibility.equals("ORGANIZATION") && r.status.equals("OPEN"), "INVITE_REQUIRED");
    add(r, access.current());
    access.audit("ROOM_JOINED", id, r.departmentId);
    return view(r);
  }

  /** 离开保留消息历史，房主须先转让。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object leave(Long id) {
    var r = db.lock(ChatRoom.class, id);
    readable(id);
    Rules.check(!Objects.equals(r.ownerId, access.current().id), "OWNER_TRANSFER_REQUIRED");
    membership(id, access.current().id).status = "LEFT";
    access.audit("ROOM_LEFT", id, r.departmentId);
    return Map.of("ok", true);
  }

  /** 房主转让给当前有效成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object transfer(Long id, Map<String, Object> b) {
    var r = managed(id);
    var a = db.get(Account.class, Rules.id(b.get("accountId")));
    Rules.check(active(a, r), "MEMBER_INACTIVE");
    r.ownerId = a.id;
    r.version++;
    access.audit("OWNER_TRANSFERRED", id, r.departmentId);
    return view(r);
  }

  /** 成员禁入持久化，旧语音票据同样受网关拦截；恢复不自动加入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object member(Long id, Long uid, Map<String, Object> b) {
    var r = managed(id);
    Rules.check(!Objects.equals(r.ownerId, uid), "OWNER_TRANSFER_REQUIRED");
    var m = membership(id, uid);
    if (m == null) throw new Problem(404, "NOT_FOUND");
    var s = Rules.text(b.get("status"), 20, true);
    Rules.check(Set.of("BANNED", "LEFT").contains(s), "INVALID_INPUT");
    m.status = s;
    access.audit("MEMBER_" + s, uid, r.departmentId);
    return Map.of("ok", true);
  }

  /** 仅成员可读房间成员，返回最少显示字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object members(Long id) {
    var r = readable(id);
    return db.query(RoomMember.class, "from RoomMember where roomId=?1", id).stream()
        .filter(m -> manager(r) || m.status.equals("ACTIVE"))
        .map(
            m -> {
              var a = db.get(Account.class, m.accountId);
              return Map.of(
                  "accountId",
                  a.id,
                  "name",
                  a.displayName,
                  "status",
                  m.status,
                  "enabled",
                  eligible(a, r),
                  "owner",
                  Objects.equals(r.ownerId, a.id));
            })
        .toList();
  }

  /** 邀请短时且限次；仅创建时返回原文，列表不返回摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object invite(Long id, Map<String, Object> b) {
    var r = managed(id);
    Rules.check(r.status.equals("OPEN"), "ROOM_CLOSED");
    Rules.check(
        db.query(
                    RoomInvite.class,
                    "from RoomInvite where roomId=?1 and revoked=false and expiresAt>?2",
                    id,
                    clock.instant())
                .size()
            < 10,
        "INVITE_LIMIT");
    byte[] bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    var code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var x = new RoomInvite();
    x.roomId = id;
    x.createdBy = access.current().id;
    x.tokenHash = hash(code);
    x.maxUses = Rules.integer(b.get("maxUses"), 1, 32);
    x.expiresAt = clock.instant().plusSeconds(admin.setting("invite_hours") * 3600L);
    db.save(x);
    access.audit("INVITE_CREATED", x.id, r.departmentId);
    return Map.of("id", x.id, "code", code, "expiresAt", x.expiresAt);
  }

  /** 撤销邀请及查看使用量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object invites(Long id) {
    managed(id);
    return db.query(RoomInvite.class, "from RoomInvite where roomId=?1", id);
  }

  /** 撤销立即阻断再次兑换。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object revoke(Long id, Long iid) {
    var r = managed(id);
    var x = db.get(RoomInvite.class, iid);
    Rules.check(x.roomId.equals(id), "INVITE_INVALID");
    x.revoked = true;
    access.audit("INVITE_REVOKED", iid, r.departmentId);
    return Map.of("ok", true);
  }

  /** 按房间行锁兑换邀请，禁入和组织范围优先于邀请有效性。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object redeem(Map<String, Object> b) {
    access.require("chat");
    String code = Rules.text(b.get("code"), 64, true);
    var rows = db.query(RoomInvite.class, "from RoomInvite where tokenHash=?1", hash(code));
    if (rows.isEmpty()) throw new Problem(404, "INVITE_INVALID");
    var x = rows.getFirst();
    var r = db.lock(ChatRoom.class, x.roomId);
    db.refresh(x);
    Rules.check(
        !x.revoked
            && x.expiresAt.isAfter(clock.instant())
            && x.uses < x.maxUses
            && r.status.equals("OPEN"),
        "INVITE_INVALID");
    var m = membership(r.id, access.current().id);
    if (m != null && m.status.equals("ACTIVE")) return view(r);
    add(r, access.current());
    x.uses++;
    access.audit("INVITE_REDEEMED", x.id, r.departmentId);
    return view(r);
  }

  /** 拉取真实持久消息；游标读取旧历史，移除消息不返回原文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object messages(Long id, long before) {
    readable(id);
    return db.messages(id, before).reversed().stream().map(this::messageView).toList();
  }

  Map<String, Object> messageView(ChatMessage m) {
    return Map.of(
        "id",
        m.id,
        "authorId",
        m.authorId,
        "author",
        db.get(Account.class, m.authorId).displayName,
        "content",
        m.removed ? "" : m.content,
        "removed",
        m.removed,
        "createdAt",
        m.createdAt);
  }

  /** 同一客户端去重键最多产生一条消息；成员退出后无法写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object send(Long id, Map<String, Object> b) {
    db.lock(ChatRoom.class, id);
    var r = writable(id);
    String nonce = Rules.text(b.get("nonce"), 40, true);
    Rules.check(nonce.matches("[a-zA-Z0-9-]{8,40}"), "INVALID_INPUT");
    String text = Rules.paragraph(b.get("content"), admin.setting("message_limit"), true);
    var prior =
        db.query(
            ChatMessage.class,
            "from ChatMessage where roomId=?1 and authorId=?2 and nonce=?3",
            id,
            access.current().id,
            nonce);
    if (!prior.isEmpty()) {
      Rules.check(
          prior.getFirst().content.equals(text) && !prior.getFirst().removed, "NONCE_CONFLICT");
      return messageView(prior.getFirst());
    }
    var m = new ChatMessage();
    m.roomId = id;
    m.authorId = access.current().id;
    m.content = text;
    m.nonce = nonce;
    m.createdAt = clock.instant();
    db.save(m);
    return messageView(m);
  }

  /** 本人或房间管理员可移除消息正文，保留时间和处理审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object remove(Long id, Long mid) {
    var r = readable(id);
    var m = db.get(ChatMessage.class, mid);
    if (!m.roomId.equals(id) || (!m.authorId.equals(access.current().id) && !manager(r)))
      throw new Problem(403, "FORBIDDEN");
    m.removed = true;
    m.content = "";
    access.audit("MESSAGE_REMOVED", mid, r.departmentId);
    return Map.of("ok", true);
  }

  /** 目录选项不返回其他成员的账号资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("chat");
    return Map.of(
        "categories",
        db.all(DictionaryEntry.class).stream().filter(c -> c.enabled).toList(),
        "capacity",
        admin.setting("room_limit"),
        "messageLimit",
        admin.setting("message_limit"));
  }

  /** 组织范围内统计，不向报表角色开放消息内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object stats() {
    access.require("reports");
    var rows =
        db.all(ChatRoom.class).stream().filter(r -> access.department(r.departmentId)).toList();
    return Map.of(
        "rooms",
        rows.size(),
        "open",
        rows.stream().filter(r -> r.status.equals("OPEN")).count(),
        "members",
        rows.stream()
            .mapToInt(
                r ->
                    db.query(
                            RoomMember.class,
                            "from RoomMember where roomId=?1 and status='ACTIVE'",
                            r.id)
                        .size())
            .sum(),
        "messages",
        rows.stream()
            .mapToLong(r -> db.count("select count(m) from ChatMessage m where m.roomId=?1", r.id))
            .sum());
  }

  /** 导出房间指标而不导出私有聊天、邀请或语音票据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String csv() {
    access.require("reports");
    var out = new StringBuilder("id,name,status,members,messages\r\n");
    for (var r : db.all(ChatRoom.class))
      if (access.department(r.departmentId))
        out.append(r.id)
            .append(',')
            .append(Rules.csv(r.name))
            .append(',')
            .append(r.status)
            .append(',')
            .append(
                db.query(
                        RoomMember.class,
                        "from RoomMember where roomId=?1 and status='ACTIVE'",
                        r.id)
                    .size())
            .append(',')
            .append(db.count("select count(m) from ChatMessage m where m.roomId=?1", r.id))
            .append("\r\n");
    return out.toString();
  }
}
