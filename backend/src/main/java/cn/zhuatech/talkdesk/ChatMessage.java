// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.persistence.*;

/** 持久消息与去重键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long roomId, authorId;

  @Column(length = 4000)
  public String content;

  public String nonce;
  public boolean removed = false;
  public java.time.Instant createdAt;
}
