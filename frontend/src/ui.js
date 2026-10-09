// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 双语操作文案，不改写聊天内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function t(zh, en) {
  return language.value === "zh" ? zh : en;
}
const errors = {
  SERVER_UNAVAILABLE: [
    "服务暂时不可用，请稍后刷新。",
    "Service temporarily unavailable. Refresh shortly.",
  ],
  UNAUTHENTICATED: [
    "会话失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号或密码不正确，或账号已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  ROOM_FORBIDDEN: [
    "你尚未加入房间，或已失去访问权限。",
    "You are not a member or room access was revoked.",
  ],
  ROOM_BANNED: ["你已被禁止进入此房间。", "You are banned from this room."],
  ROOM_CLOSED: [
    "房间已锁定或归档，不能发送或进入语音。",
    "Room locked or archived. Sending and voice are unavailable.",
  ],
  ROOM_FULL: ["房间人数已满。", "Room capacity reached."],
  INVITE_INVALID: [
    "邀请已失效、用完或不适用于你的组织。",
    "Invitation invalid, expired or exhausted.",
  ],
  INVITE_REQUIRED: [
    "私有房间需要有效邀请。",
    "Private rooms require an invitation.",
  ],
  INVITE_LIMIT: [
    "有效邀请已达上限，请先撤销旧邀请。",
    "Revoke an old invitation before creating another.",
  ],
  OWNER_TRANSFER_REQUIRED: [
    "请先将房主转让给有效成员。",
    "Transfer ownership to an active member first.",
  ],
  MEMBER_INACTIVE: [
    "接收房主的账号必须是有效成员。",
    "The new owner must be an active member.",
  ],
  ROOM_ARCHIVED: [
    "归档房间不可重新编辑或开放。",
    "Archived rooms cannot be edited or reopened.",
  ],
  ROOM_STATE: ["房间状态已经变化，请刷新。", "Room state changed. Refresh."],
  VOICE_UNAVAILABLE: [
    "语音服务不可用，请检查部署状态后重试。",
    "Voice service unavailable. Check deployment and retry.",
  ],
  VOICE_CONNECT_FAILED: [
    "语音连接未建立。请重试；持续失败请联系系统管理员。",
    "Voice connection failed. Retry; contact your administrator if it persists.",
  ],
  VOICE_DENIED: [
    "语音授权失效，请重新进入房间。",
    "Voice authorization expired. Rejoin the room.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大小写字母和数字。",
    "Password needs 12–72 bytes with uppercase, lowercase and digits.",
  ],
  LAST_ADMIN: [
    "必须保留一位启用的完整管理员。",
    "Keep an enabled full administrator.",
  ],
  VERSION_CONFLICT: [
    "记录已被修改，请刷新核对后保存。",
    "Record changed. Refresh before saving.",
  ],
  CATEGORY_DISABLED: [
    "分类已停用，请选择有效分类。",
    "Select an enabled category.",
  ],
  INVALID_INPUT: [
    "字段格式不正确，请检查表单。",
    "Invalid fields. Check the form.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Current password is incorrect."],
  CONFLICT: [
    "重复记录或数据仍被使用，请核对。",
    "Duplicate or referenced data. Check input.",
  ],
  NETWORK_ERROR: [
    "连接失败，请检查网络后刷新。",
    "Connection failed. Check network and refresh.",
  ],
  RESULT_UNKNOWN: [
    "连接中断，提交结果未知。请刷新核对，勿重复提交。",
    "Connection interrupted; result unknown. Refresh before resubmitting.",
  ],
  NONCE_CONFLICT: [
    "消息提交标识重复，内容未保存。",
    "Message identifier reused with different content.",
  ],
  OUT_OF_SCOPE: ["记录不在你的组织范围。", "Record outside your organization."],
};
/** 显示业务错误及下一步操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function errorMessage(e) {
  return errors[e.message]
    ? t(...errors[e.message])
    : `${t("操作未完成", "Action failed")} (${e.message})`;
}
export const confirmation = ref(null);
/** 页面内操作确认。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
/** 完成确认。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function answerConfirmation(ok) {
  const p = confirmation.value;
  confirmation.value = null;
  p?.resolve(ok);
}
/** 编辑记录副本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cloneRecord(r) {
  return JSON.parse(JSON.stringify(r));
}
/** 时间按当前语言显示，不假造时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dateTime(s) {
  return s
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(s))
    : "—";
}
/** 显示可理解的审计动作，数据库仍保留稳定代码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actionName(code) {
  const names = {
    LOGIN: ["账号登录", "Sign-in"],
    PASSWORD_CHANGE: ["修改本人密码", "Own password changed"],
    ROOM_CREATED: ["创建房间", "Room created"],
    ROOM_UPDATED: ["更新房间", "Room updated"],
    ROOM_OPEN: ["重新开放房间", "Room reopened"],
    ROOM_LOCKED: ["锁定房间", "Room locked"],
    ROOM_ARCHIVED: ["归档房间", "Room archived"],
    ROOM_JOINED: ["加入房间", "Room joined"],
    ROOM_LEFT: ["离开房间", "Room left"],
    OWNER_TRANSFERRED: ["转让房主", "Ownership transferred"],
    MEMBER_BANNED: ["成员禁入", "Member banned"],
    MEMBER_LEFT: ["解除禁入", "Member unbanned"],
    INVITE_CREATED: ["生成邀请", "Invitation created"],
    INVITE_REVOKED: ["撤销邀请", "Invitation revoked"],
    INVITE_REDEEMED: ["兑换邀请", "Invitation redeemed"],
    MESSAGE_REMOVED: ["移除消息", "Message removed"],
    VOICE_JOIN_AUTHORIZED: ["加入语音授权", "Voice join authorized"],
    ADMIN_USERS: ["维护登录账号", "Account updated"],
    ADMIN_ROLES: ["维护角色", "Role updated"],
    ADMIN_PERMISSIONS: ["维护权限目录", "Permission label updated"],
    ADMIN_DEPARTMENTS: ["维护组织", "Organization updated"],
    ADMIN_DICTIONARIES: ["维护分类", "Category updated"],
    ADMIN_MENUS: ["维护菜单", "Menu updated"],
    ADMIN_SETTINGS: ["维护参数", "Parameter updated"],
  };
  return names[code] ? t(...names[code]) : code;
}
