// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import jakarta.persistence.*;

/** 短时语音授权与撤销检查凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class VoiceLease {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long roomId, accountId;
  public String identity, physicalRoom;
  @com.fasterxml.jackson.annotation.JsonIgnore public String credential;
  public java.time.Instant expiresAt;
  public boolean revoked = false;
  @com.fasterxml.jackson.annotation.JsonIgnore public String sessionKey;
}
