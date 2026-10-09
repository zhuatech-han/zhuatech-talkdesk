// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.talkdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

/** 真实身份、数据库迁移、业务HTTP与权限验证；仅媒体服务外部调用替身。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatIntegrationTest {
  static final String PW = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:talkdesk;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("talkdesk.admin-password", () -> PW);
    r.add("talkdesk.voice-key", () -> "testing");
    r.add("talkdesk.voice-secret", () -> "TestingOnlyNotAPublishedCredential-0123456789");
    r.add("talkdesk.reconcile-enabled", () -> false);
  }

  @Autowired MockMvc mvc;
  @Autowired Store db;
  @Autowired TransactionTemplate tx;
  @MockitoBean VoiceGateway rtc;
  final ObjectMapper json = new ObjectMapper();
  MockHttpSession admin, alice, bob, other, viewer, charlie;
  long aliceId, bobId;

  Map<String, Object> m(Object... v) {
    var x = new LinkedHashMap<String, Object>();
    for (int i = 0; i < v.length; i += 2) x.put(v[i].toString(), v[i + 1]);
    return x;
  }

  MvcResult req(MockHttpSession s, String p, String method, Object b) throws Exception {
    var q =
        switch (method) {
          case "GET" -> get("/api" + p);
          case "POST" -> post("/api" + p);
          case "PUT" -> put("/api" + p);
          case "DELETE" -> delete("/api" + p);
          default -> throw new IllegalArgumentException();
        };
    if (s != null) q.session(s);
    if (!method.equals("GET")) q.with(csrf());
    if (b != null) q.contentType("application/json").content(json.writeValueAsString(b));
    return mvc.perform(q).andReturn();
  }

  JsonNode ok(MockHttpSession s, String p, String method, Object b) throws Exception {
    var r = req(s, p, method, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession s, String p, String method, Object b, int code) throws Exception {
    assertEquals(code, req(s, p, method, b).getResponse().getStatus());
  }

  MockHttpSession login(String name) throws Exception {
    var r = req(null, "/auth/login", "POST", m("username", name, "password", PW));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession();
  }

  long user(String name, long role, long org) throws Exception {
    return ok(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                name,
                "password",
                PW,
                "roleId",
                role,
                "departmentId",
                org,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  @BeforeAll
  void init() throws Exception {
    admin = login("admin");
    long org =
        ok(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST Other", "zone", "UTC", "enabled", true))
            .get("id")
            .asLong();
    aliceId = user("alice", 3, 1);
    bobId = user("bob", 3, 1);
    user("outsider", 3, org);
    user("viewer", 4, 1);
    alice = login("alice");
    bob = login("bob");
    other = login("outsider");
    viewer = login("viewer");
    user("charlie", 3, 1);
    charlie = login("charlie");
  }

  @BeforeEach
  void mediaFixture() {
    when(rtc.credential(any()))
        .thenAnswer(x -> ChatService.hash(((Account) x.getArgument(0)).passwordHash));
    when(rtc.token(any(), any(), any())).thenAnswer(invocation -> "test-join-token");
  }

  JsonNode room(String visibility, int cap) throws Exception {
    return ok(
        alice,
        "/rooms",
        "POST",
        m(
            "name",
            "TEST " + UUID.randomUUID(),
            "description",
            "Test collaboration",
            "category",
            "TEAM",
            "visibility",
            visibility,
            "capacity",
            cap));
  }

  String path(JsonNode r) {
    return "/rooms/" + r.get("id").asLong();
  }

  void joinBob(JsonNode r) throws Exception {
    ok(bob, path(r) + "/join", "POST", null);
  }

  JsonNode invite(JsonNode r, int uses) throws Exception {
    return ok(alice, path(r) + "/invites", "POST", m("maxUses", uses));
  }

  JsonNode send(MockHttpSession s, JsonNode r, String text) throws Exception {
    return ok(
        s,
        path(r) + "/messages",
        "POST",
        m("content", text, "nonce", UUID.randomUUID().toString()));
  }

  @Test
  void bootstrapExposesPermittedMenus() throws Exception {
    assertEquals(1, ok(alice, "/auth/me", "GET", null).get("menus").size());
    assertEquals(6, ok(admin, "/auth/me", "GET", null).get("menus").size());
  }

  @Test
  void anonymousDenied() throws Exception {
    fail(null, "/rooms", "GET", null, 401);
  }

  @Test
  void writesRequireCsrf() throws Exception {
    assertEquals(
        403,
        mvc.perform(post("/api/rooms").session(alice).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void passwordNeverReturned() throws Exception {
    String s = ok(admin, "/admin/users", "GET", null).toString();
    assertFalse(s.contains("passwordHash"));
    assertFalse(s.contains(PW));
  }

  @Test
  void privateRoomIsHidden() throws Exception {
    var r = room("PRIVATE", 3);
    assertFalse(ok(bob, "/rooms", "GET", null).toString().contains(r.get("name").asText()));
    fail(bob, path(r) + "/messages", "GET", null, 403);
  }

  @Test
  void organizationScopeEnforced() throws Exception {
    var r = room("ORGANIZATION", 3);
    fail(other, path(r) + "/join", "POST", null, 409);
  }

  @Test
  void memberJoinsPublicRoom() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    assertEquals(2, ok(bob, path(r) + "/members", "GET", null).size());
  }

  @Test
  void privateDirectJoinDenied() throws Exception {
    var r = room("PRIVATE", 3);
    fail(bob, path(r) + "/join", "POST", null, 409);
  }

  @Test
  void invitationJoinsPrivateRoom() throws Exception {
    var r = room("PRIVATE", 3);
    var i = invite(r, 1);
    ok(bob, "/invites/redeem", "POST", m("code", i.get("code").asText()));
    assertEquals(2, ok(alice, path(r) + "/members", "GET", null).size());
  }

  @Test
  void revokedInvitationRejected() throws Exception {
    var r = room("PRIVATE", 3);
    var i = invite(r, 2);
    ok(alice, path(r) + "/invites/" + i.get("id").asLong() + "/revoke", "POST", null);
    fail(bob, "/invites/redeem", "POST", m("code", i.get("code").asText()), 409);
  }

  @Test
  void invitationDigestNotReturned() throws Exception {
    var r = room("PRIVATE", 3);
    invite(r, 2);
    assertFalse(ok(alice, path(r) + "/invites", "GET", null).toString().contains("tokenHash"));
  }

  @Test
  void invitationCannotCrossOrganization() throws Exception {
    var r = room("PRIVATE", 3);
    var i = invite(r, 2);
    fail(other, "/invites/redeem", "POST", m("code", i.get("code").asText()), 409);
  }

  @Test
  void capacityCannotOverfill() throws Exception {
    var r = room("ORGANIZATION", 1);
    fail(bob, path(r) + "/join", "POST", null, 409);
  }

  @Test
  void idempotentJoinDoesNotConsumeCapacity() throws Exception {
    var r = room("ORGANIZATION", 2);
    joinBob(r);
    joinBob(r);
    assertEquals(2, ok(alice, path(r) + "/members", "GET", null).size());
  }

  @Test
  void messagePersistsAcrossReads() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    var sent = send(bob, r, "TEST hello");
    assertEquals(sent.get("id"), ok(alice, path(r) + "/messages", "GET", null).get(0).get("id"));
  }

  @Test
  void messageDuplicatesAreIdempotent() throws Exception {
    var r = room("PRIVATE", 3);
    var b = m("content", "test duplicate", "nonce", UUID.randomUUID().toString());
    var a = ok(alice, path(r) + "/messages", "POST", b);
    var c = ok(alice, path(r) + "/messages", "POST", b);
    assertEquals(a.get("id"), c.get("id"));
  }

  @Test
  void reusedNonceCannotChangeContent() throws Exception {
    var r = room("PRIVATE", 3);
    var b = m("content", "first", "nonce", UUID.randomUUID().toString());
    ok(alice, path(r) + "/messages", "POST", b);
    b.put("content", "changed");
    fail(alice, path(r) + "/messages", "POST", b, 409);
  }

  @Test
  void oversizedMessageRejected() throws Exception {
    var r = room("PRIVATE", 3);
    fail(
        alice,
        path(r) + "/messages",
        "POST",
        m("content", "a".repeat(2001), "nonce", UUID.randomUUID().toString()),
        400);
  }

  @Test
  void messageControlCharactersRejected() throws Exception {
    var r = room("PRIVATE", 3);
    fail(
        alice,
        path(r) + "/messages",
        "POST",
        m("content", "bad\u0000text", "nonce", UUID.randomUUID().toString()),
        400);
  }

  @Test
  void authorMayRemoveOwnMessage() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    var a = send(bob, r, "private removed");
    ok(bob, path(r) + "/messages/" + a.get("id").asLong(), "DELETE", null);
    var rows = ok(alice, path(r) + "/messages", "GET", null);
    assertTrue(rows.get(0).get("removed").asBoolean());
    assertEquals("", rows.get(0).get("content").asText());
  }

  @Test
  void memberCannotRemoveOthersMessage() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    var a = send(alice, r, "owner message");
    fail(bob, path(r) + "/messages/" + a.get("id").asLong(), "DELETE", null, 403);
  }

  @Test
  void messageRoomMismatchDenied() throws Exception {
    var r = room("PRIVATE", 3);
    var a = send(alice, r, "one");
    var second = room("PRIVATE", 3);
    fail(alice, path(second) + "/messages/" + a.get("id").asLong(), "DELETE", null, 403);
  }

  @Test
  void historyCursorDoesNotMixRooms() throws Exception {
    var r = room("PRIVATE", 3);
    var first = send(alice, r, "first");
    var second = send(alice, r, "second");
    var rows = ok(alice, path(r) + "/messages?before=" + second.get("id").asLong(), "GET", null);
    assertEquals(1, rows.size());
    assertEquals(first.get("id"), rows.get(0).get("id"));
  }

  @Test
  void ownerCannotLeaveWithoutTransfer() throws Exception {
    var r = room("PRIVATE", 3);
    fail(alice, path(r) + "/leave", "POST", null, 409);
  }

  @Test
  void ownerTransfersThenLeaves() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    ok(alice, path(r) + "/owner", "POST", m("accountId", bobId));
    ok(alice, path(r) + "/leave", "POST", null);
    fail(alice, path(r) + "/messages", "GET", null, 403);
  }

  @Test
  void bannedMemberCannotRejoinOrRead() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    ok(alice, path(r) + "/members/" + bobId, "PUT", m("status", "BANNED"));
    fail(bob, path(r) + "/join", "POST", null, 409);
    fail(bob, path(r) + "/messages", "GET", null, 403);
  }

  @Test
  void unbanRequiresExplicitRejoin() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    ok(alice, path(r) + "/members/" + bobId, "PUT", m("status", "BANNED"));
    ok(alice, path(r) + "/members/" + bobId, "PUT", m("status", "LEFT"));
    fail(bob, path(r) + "/messages", "GET", null, 403);
    joinBob(r);
  }

  @Test
  void ordinaryMemberCannotBanOwner() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    fail(bob, path(r) + "/members/" + aliceId, "PUT", m("status", "BANNED"), 403);
  }

  @Test
  void lockedRoomRetainsReadButBlocksWrites() throws Exception {
    var r = room("ORGANIZATION", 3);
    send(alice, r, "history");
    ok(alice, path(r) + "/state", "POST", m("version", 1, "status", "LOCKED"));
    assertEquals(1, ok(alice, path(r) + "/messages", "GET", null).size());
    fail(
        alice,
        path(r) + "/messages",
        "POST",
        m("content", "new", "nonce", UUID.randomUUID().toString()),
        409);
    fail(alice, path(r) + "/voice", "POST", null, 409);
  }

  @Test
  void archivedRoomCannotReopen() throws Exception {
    var r = room("PRIVATE", 3);
    ok(alice, path(r) + "/state", "POST", m("version", 1, "status", "ARCHIVED"));
    fail(alice, path(r) + "/state", "POST", m("version", 2, "status", "OPEN"), 409);
  }

  @Test
  void staleVersionRejected() throws Exception {
    var r = room("PRIVATE", 3);
    ok(alice, path(r) + "/state", "POST", m("version", 1, "status", "LOCKED"));
    fail(alice, path(r) + "/state", "POST", m("version", 1, "status", "OPEN"), 409);
  }

  @Test
  void unauthorizedAdminAccessDenied() throws Exception {
    fail(alice, "/admin/users", "GET", null, 403);
    fail(alice, "/admin/roles", "POST", m(), 403);
  }

  @Test
  void lastAdministratorProtected() throws Exception {
    var a = ok(admin, "/admin/users", "GET", null).get(0);
    var b = json.convertValue(a, Map.class);
    b.put("enabled", false);
    fail(admin, "/admin/users/" + a.get("id").asLong(), "PUT", b, 409);
  }

  @Test
  void reportViewerCannotReadChat() throws Exception {
    fail(viewer, "/rooms", "GET", null, 403);
    ok(viewer, "/stats", "GET", null);
  }

  @Test
  void reportExportExcludesSecrets() throws Exception {
    var r = room("PRIVATE", 3);
    send(alice, r, "SECRET-TEXT-NEVER-IN-CSV");
    String csv = req(admin, "/reports/rooms.csv", "GET", null).getResponse().getContentAsString();
    assertFalse(csv.contains("SECRET-TEXT"));
    assertFalse(csv.contains("token"));
  }

  @Test
  void auditExcludesChatContent() throws Exception {
    var r = room("PRIVATE", 3);
    send(alice, r, "DO-NOT-AUDIT-CONTENT");
    assertFalse(ok(admin, "/audit", "GET", null).toString().contains("DO-NOT-AUDIT-CONTENT"));
  }

  @Test
  void voiceOnlyAvailableToMembers() throws Exception {
    var r = room("PRIVATE", 3);
    fail(bob, path(r) + "/voice", "POST", null, 403);
    var v = ok(alice, path(r) + "/voice", "POST", null);
    assertEquals("/voice", v.get("url").asText());
    assertFalse(v.toString().contains("secret"));
  }

  @Test
  void banRevokesExistingVoiceGatewayLease() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    var v = ok(bob, path(r) + "/voice", "POST", null);
    when(rtc.verify("gateway-ban-test"))
        .thenAnswer(
            invocation ->
                m(
                    "sub",
                    v.get("identity").asText(),
                    "video",
                    m("room", "talk-" + r.get("id").asLong() + "-1", "roomJoin", true)));
    assertEquals(
        200,
        mvc.perform(get("/api/voice/authorize").header("X-Voice-Token", "gateway-ban-test"))
            .andReturn()
            .getResponse()
            .getStatus());
    ok(alice, path(r) + "/members/" + bobId, "PUT", m("status", "BANNED"));
    assertEquals(
        403,
        mvc.perform(get("/api/voice/authorize").header("X-Voice-Token", "gateway-ban-test"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void roomGenerationInvalidatesOldVoiceLease() throws Exception {
    var r = room("PRIVATE", 3);
    var v = ok(alice, path(r) + "/voice", "POST", null);
    when(rtc.verify("gateway-state-test"))
        .thenAnswer(
            invocation ->
                m(
                    "sub",
                    v.get("identity").asText(),
                    "video",
                    m("room", "talk-" + r.get("id").asLong() + "-1", "roomJoin", true)));
    ok(alice, path(r) + "/state", "POST", m("version", 1, "status", "LOCKED"));
    ok(alice, path(r) + "/state", "POST", m("version", 2, "status", "OPEN"));
    assertEquals(
        403,
        mvc.perform(get("/api/voice/authorize").header("X-Voice-Token", "gateway-state-test"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void otherMemberCannotRevokeVoiceLease() throws Exception {
    var r = room("ORGANIZATION", 3);
    joinBob(r);
    var v = ok(alice, path(r) + "/voice", "POST", null);
    fail(bob, "/voice/" + v.get("leaseId").asLong() + "/leave", "POST", null, 403);
  }

  @Test
  void concurrentJoinCannotOverfillRoom() throws Exception {
    var r = room("ORGANIZATION", 2);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> req(bob, path(r) + "/join", "POST", null).getResponse().getStatus());
      var second =
          executor.submit(
              () -> req(charlie, path(r) + "/join", "POST", null).getResponse().getStatus());
      var codes = new ArrayList<Integer>(List.of(first.get(), second.get()));
      java.util.Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
      assertEquals(2, ok(alice, path(r) + "/members", "GET", null).size());
    }
  }

  @Test
  void logoutRevokesThisSessionsVoiceLease() throws Exception {
    var r = room("ORGANIZATION", 3);
    var session = login("bob");
    ok(session, path(r) + "/join", "POST", null);
    var v = ok(session, path(r) + "/voice", "POST", null);
    when(rtc.verify("logout-test"))
        .thenAnswer(
            invocation ->
                m(
                    "sub",
                    v.get("identity").asText(),
                    "video",
                    m("room", "talk-" + r.get("id").asLong() + "-1", "roomJoin", true)));
    ok(session, "/auth/logout", "POST", null);
    assertEquals(
        403,
        mvc.perform(get("/api/voice/authorize").header("X-Voice-Token", "logout-test"))
            .andReturn()
            .getResponse()
            .getStatus());
  }
}
