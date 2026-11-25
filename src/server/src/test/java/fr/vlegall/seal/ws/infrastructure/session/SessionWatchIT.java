package fr.vlegall.seal.ws.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SessionRevocation;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestWebSocketClient;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TRN-12, AUTH-52, AUTH-53 : chaque connexion suit la session HTTP à laquelle elle est liée. */
@SealServerTest
class SessionWatchIT {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SessionWatch watch;

    @Autowired
    private SessionRevocation revocation;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    private TestAccount account;

    @BeforeEach
    void openAccount() throws Exception {
        account = TestAccount.fresh(mvc, mail, "veille");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private TestWebSocketClient.Connection connect(Cookie session) throws Exception {
        return new TestWebSocketClient(port).connect(session, TestWebSocketClient.ORIGIN);
    }

    /** Identifiant de session que porte le cookie (Spring Session l'encode en base64). */
    private static String sessionId(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    private Instant lastAccess(Cookie cookie) {
        Session session = sessions.findById(sessionId(cookie));
        assertThat(session).as("session en base").isNotNull();
        return session.getLastAccessedTime();
    }

    private Cookie secondSession() throws Exception {
        MvcResult login = mvc.perform(account.client.unsafe(
                        post("/api/v1/auth/login"),
                        null,
                        Map.of("login", account.username, "password", AuthClient.PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return AuthClient.sessionOf(login);
    }

    private static String send(TestWebSocketClient.Connection connection, String type, String id) throws Exception {
        connection.send("{\"type\":\"" + type + "\",\"id\":\"" + id + "\",\"payload\":{}}");
        String reply = connection.next(WAIT);
        assertThat(reply).as("réponse à " + id).isNotNull();
        return JSON.readTree(reply).get("type").asString();
    }

    private static void expectRevokedThenClosed(TestWebSocketClient.Connection connection) throws Exception {
        String frame = connection.next(WAIT);
        assertThat(frame).as("trame session.revoked").isNotNull();
        JsonNode revoked = JSON.readTree(frame);
        assertThat(revoked.get("type").asString()).isEqualTo("session.revoked");
        assertThat(connection.closeCode(WAIT)).isEqualTo(4401);
    }

    private static void expectOpen(TestWebSocketClient.Connection connection) throws Exception {
        assertThat(send(connection, "ping", "encore")).isEqualTo("pong");
    }

    @Test
    @DisplayName(
            "AUTH-53, TRN-12 : la déconnexion envoie session.revoked puis ferme en 4401 les seules connexions de cette session")
    void auth53LogoutClosesOnlyThisSession() throws Exception {
        Cookie other = secondSession();
        TestWebSocketClient.Connection mine = connect(account.session);
        TestWebSocketClient.Connection elsewhere = connect(other);

        mvc.perform(account.unsafe(post("/api/v1/auth/logout"), null)).andExpect(status().isNoContent());

        expectRevokedThenClosed(mine);
        expectOpen(elsewhere);
    }

    @Test
    @DisplayName(
            "AUTH-50, TRN-12 : révoquer les autres sessions ferme leurs connexions, pas celle de la session gardée")
    void auth50RevokingOthersClosesTheirConnections() throws Exception {
        Cookie other = secondSession();
        TestWebSocketClient.Connection kept = connect(account.session);
        TestWebSocketClient.Connection revoked = connect(other);

        revocation.revokeOthers(new UserId(account.id), sessionId(account.session));

        expectRevokedThenClosed(revoked);
        expectOpen(kept);
    }

    @Test
    @DisplayName(
            "TRN-12, A10 : une trame sans action après l'expiration n'est pas traitée non plus ; la connexion est fermée")
    void trn12NonActionFrameAfterExpiryIsRefused() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);

        clock.advance(Duration.ofMinutes(31));
        connection.send("{\"type\":\"test.peek\",\"id\":\"tard\",\"payload\":{}}");

        expectRevokedThenClosed(connection);
    }

    @Test
    @DisplayName("AUTH-52, ADR 0043 : une trame sans action de l'utilisateur ne prolonge jamais la session")
    void auth52NonActionFrameNeverExtendsTheSession() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);
        Instant before = lastAccess(account.session);

        clock.advance(Duration.ofSeconds(61));
        assertThat(send(connection, "test.peek", "v1")).isEqualTo("ack");

        assertThat(lastAccess(account.session)).isEqualTo(before);
    }

    @Test
    @DisplayName("AUTH-52, A10 : une trame d'action après l'expiration n'est pas traitée ; la connexion est fermée")
    void auth52UserActionAfterExpiryIsRefused() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);

        clock.advance(Duration.ofMinutes(31));
        connection.send("{\"type\":\"test.echo\",\"id\":\"tard\",\"payload\":{}}");

        expectRevokedThenClosed(connection);
    }

    @Test
    @DisplayName("AUTH-52, TRN-12 : après 30 minutes d'inactivité, la ronde ferme la connexion en 4401")
    void auth52ExpiredSessionIsClosedByTheSweep() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);

        clock.advance(Duration.ofMinutes(31));
        watch.sweep();

        expectRevokedThenClosed(connection);
    }

    @Test
    @DisplayName("TRN-13 : 60 secondes sans trame ferment la connexion en 4408")
    void trn13SilentConnectionIsClosed() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);

        clock.advance(Duration.ofSeconds(61));
        watch.sweep();

        assertThat(connection.closeCode(WAIT)).isEqualTo(4408);
    }

    @Test
    @DisplayName("TRN-13 : une trame dans la minute garde la connexion ouverte")
    void trn13FrameWithinAMinuteKeepsTheConnection() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);

        clock.advance(Duration.ofSeconds(40));
        send(connection, "ping", "a");
        clock.advance(Duration.ofSeconds(40));
        watch.sweep();

        expectOpen(connection);
    }

    @Test
    @DisplayName("AUTH-52, ADR 0043 : des ping seuls, pendant 3 minutes, ne prolongent jamais la session")
    void auth52PingNeverExtendsTheSession() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);
        Instant before = lastAccess(account.session);

        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofSeconds(30));
            send(connection, "ping", "p" + i);
            watch.sweep();
        }

        assertThat(lastAccess(account.session)).isEqualTo(before);
    }

    @Test
    @DisplayName("AUTH-52, ADR 0043 : une trame d'action prolonge la session, au plus une fois par minute")
    void auth52UserActionExtendsTheSessionOncePerMinute() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);
        Instant before = lastAccess(account.session);

        clock.advance(Duration.ofSeconds(61));
        assertThat(send(connection, "test.echo", "a1")).isEqualTo("ack");
        Instant first = lastAccess(account.session);
        clock.advance(Duration.ofSeconds(20));
        assertThat(send(connection, "test.echo", "a2")).isEqualTo("ack");
        Instant throttled = lastAccess(account.session);
        clock.advance(Duration.ofSeconds(41));
        assertThat(send(connection, "test.echo", "a3")).isEqualTo("ack");

        assertThat(first).isAfter(before);
        assertThat(throttled).isEqualTo(first);
        assertThat(lastAccess(account.session)).isAfter(first);
    }

    @Test
    @DisplayName(
            "AUTH-55, TRN-12 : une ré-authentification renouvelle la session sans fermer la connexion, qui la suit")
    void auth55RenewalKeepsTheConnectionBound() throws Exception {
        TestWebSocketClient.Connection connection = connect(account.session);
        MvcResult reauth = mvc.perform(account.client.unsafe(
                        post("/api/v1/auth/reauth"), account.session, Map.of("password", AuthClient.PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie renewed = AuthClient.sessionOf(reauth);

        watch.sweep();
        expectOpen(connection);
        mvc.perform(account.client.unsafe(post("/api/v1/auth/logout"), renewed, null))
                .andExpect(status().isNoContent());

        expectRevokedThenClosed(connection);
    }
}
