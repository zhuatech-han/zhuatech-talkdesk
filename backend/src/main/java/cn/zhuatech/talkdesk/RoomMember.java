// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.persistence.*;

/** 加入、离开与禁止再次进入的成员关系。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class RoomMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long roomId, accountId;
  public String status = "ACTIVE";
  public java.time.Instant joinedAt;
}
