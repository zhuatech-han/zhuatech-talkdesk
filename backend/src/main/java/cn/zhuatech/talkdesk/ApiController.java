// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 业务及系统接口，权限与状态交由领域服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final ChatService chat;
  final AdminService admin;
  final VoiceService voice;
  final Store db;
  final AccessService access;

  public ApiController(ChatService c, AdminService a, VoiceService v, Store d, AccessService x) {
    chat = c;
    admin = a;
    voice = v;
    db = d;
    access = x;
  }

  /** 读取可见房间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rooms")
  public Object rooms() {
    return chat.rooms();
  }

  /** 创建房间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms")
  public Object create(@RequestBody Map<String, Object> b) {
    return chat.create(b);
  }

  /** 更新房间配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/rooms/{id}")
  public Object update(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return chat.update(id, b);
  }

  /** 锁定或归档。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/state")
  public Object state(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return chat.state(id, b);
  }

  /** 组织房加入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/join")
  public Object join(@PathVariable Long id) {
    return chat.join(id);
  }

  /** 离开成员关系。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/leave")
  public Object leave(@PathVariable Long id) {
    return chat.leave(id);
  }

  /** 转让房主。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/owner")
  public Object transfer(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return chat.transfer(id, b);
  }

  /** 成员禁入或恢复。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/rooms/{id}/members/{uid}")
  public Object member(
      @PathVariable Long id, @PathVariable Long uid, @RequestBody Map<String, Object> b) {
    return chat.member(id, uid, b);
  }

  /** 当前房间成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rooms/{id}/members")
  public Object members(@PathVariable Long id) {
    return chat.members(id);
  }

  /** 生成限次邀请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/invites")
  public Object invite(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return chat.invite(id, b);
  }

  /** 邀请状态不返回凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rooms/{id}/invites")
  public Object invites(@PathVariable Long id) {
    return chat.invites(id);
  }

  /** 撤销邀请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/invites/{iid}/revoke")
  public Object revoke(@PathVariable Long id, @PathVariable Long iid) {
    return chat.revoke(id, iid);
  }

  /** 兑换邀请加入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invites/redeem")
  public Object redeem(@RequestBody Map<String, Object> b) {
    return chat.redeem(b);
  }

  /** 最近消息及历史游标。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rooms/{id}/messages")
  public Object messages(
      @PathVariable Long id, @RequestParam(defaultValue = "9223372036854775807") long before) {
    return chat.messages(id, before);
  }

  /** 写入持久消息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/messages")
  public Object send(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return chat.send(id, b);
  }

  /** 移除消息正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/rooms/{id}/messages/{mid}")
  public Object remove(@PathVariable Long id, @PathVariable Long mid) {
    return chat.remove(id, mid);
  }

  /** 最小目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return chat.options();
  }

  /** 新建受控语音租约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rooms/{id}/voice")
  public Object voice(@PathVariable Long id, HttpServletRequest req) {
    return voice.join(id, req.getSession().getId());
  }

  /** 主动撤销语音。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/voice/{lid}/leave")
  public Object end(@PathVariable Long lid) {
    return voice.leave(lid);
  }

  /** Nginx内部鉴权子请求；外部路径不开放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/voice/authorize")
  public Object authorize(@RequestHeader(value = "X-Voice-Token", required = false) String token) {
    voice.authorize(token);
    return Map.of("ok", true);
  }

  /** 后台目录读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object read(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 新增后台目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object save(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 版本化后台目录更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object edit(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  /** 组织指标。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/stats")
  public Object stats() {
    return chat.stats();
  }

  /** 不含聊天正文的CSV。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports/rooms.csv")
  public ResponseEntity<String> csv() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=talkdesk-rooms.csv")
        .header("Cache-Control", "no-store")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(chat.csv());
  }

  /** 审计按组织过滤，不记录消息或令牌正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .toList();
  }
}
