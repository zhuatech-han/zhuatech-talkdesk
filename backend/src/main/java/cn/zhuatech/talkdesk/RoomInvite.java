// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.persistence.*;

/** 仅保存邀请摘要及有效期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class RoomInvite {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long roomId, createdBy;
  @com.fasterxml.jackson.annotation.JsonIgnore public String tokenHash;
  public java.time.Instant expiresAt;
  public int maxUses, uses = 0;
  public boolean revoked = false;
}
