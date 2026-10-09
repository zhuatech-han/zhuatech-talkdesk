// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.persistence.*;

/** 房间及归档状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class ChatRoom {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String name;

  @Column(length = 1000)
  public String description = "";

  public String category;
  public Long departmentId, ownerId;
  public String visibility = "PRIVATE", status = "OPEN";
  public int capacity = 16;
  public long version = 1, generation = 1;
  public java.time.Instant createdAt;
}
