package fr.vlegall.seal.ws.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthFlow.Started;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestWebSocketClient;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** TRN-12, AUTH-55, ADR 0044 : une ré-authentification OAuth, qui renomme deux fois la session, garde la WebSocket. */
@SealServerTest
@Import(OAuthTestConfiguration.class)
class OAuthRenewalIT {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    @Autowired
    private OAuthIdentityRepository identities;

    @Autowired
    private SessionWatch watch;

    @AfterEach
    void reset() {
        fake.reset();
    }

    private static String subject() {
        return "1" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    private static String type(String frame) {
        return JSON.readTree(frame).get("type").asString();
    }

    private static void expectOpen(TestWebSocketClient.Connection connection) throws Exception {
        connection.send("{\"type\":\"ping\",\"id\":\"encore\"}");
        String reply = connection.next(WAIT);
        assertThat(reply).as("réponse au ping").isNotNull();
        assertThat(type(reply)).isEqualTo("pong");
    }

    @Test
    @DisplayName(
            "AUTH-55, TRN-12 : après une ré-authentification OAuth, la connexion suit la session ; la déconnexion la ferme")
    void auth55OAuthReauthenticationKeepsTheConnection() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        String run = UUID.randomUUID().toString().substring(0, 6);
        String subject = subject();
        Registered account = client.register("oauthws" + run + "@exemple.ca", "oauthws" + run);
        Cookie full = client.verify(account);
        identities.link(
                new UserId(account.id()),
                ExternalIdentity.of(OAuthProvider.GOOGLE, subject, account.email(), true, "Nom"),
                Instant.now());
        TestWebSocketClient.Connection connection =
                new TestWebSocketClient(port).connect(full, TestWebSocketClient.ORIGIN);

        OAuthFlow oauth = new OAuthFlow(mvc, client, fake);
        Started started = oauth.start("google", full);
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(subject, "autre@gmail.com")));
        Cookie renewed = AuthClient.sessionOf(result);
        assertThat(renewed.getValue()).isNotEqualTo(full.getValue());

        watch.sweep();
        expectOpen(connection);
        mvc.perform(client.unsafe(post("/api/v1/auth/logout"), renewed, null)).andExpect(status().isNoContent());
        String revoked = connection.next(WAIT);
        assertThat(revoked).as("session.revoked").isNotNull();
        assertThat(type(revoked)).isEqualTo("session.revoked");
        assertThat(connection.closeCode(WAIT)).isEqualTo(4401);
    }

    @Test
    @DisplayName("AUTH-55, TRN-12 : une identité refusée, que Spring renomme sans nous, garde aussi la connexion")
    void auth55RefusedIdentityKeepsTheConnection() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        String run = UUID.randomUUID().toString().substring(0, 6);
        Registered account = client.register("oauthko" + run + "@exemple.ca", "oauthko" + run);
        Cookie full = client.verify(account);
        identities.link(
                new UserId(account.id()),
                ExternalIdentity.of(OAuthProvider.GOOGLE, subject(), account.email(), true, "Nom"),
                Instant.now());
        TestWebSocketClient.Connection connection =
                new TestWebSocketClient(port).connect(full, TestWebSocketClient.ORIGIN);

        OAuthFlow oauth = new OAuthFlow(mvc, client, fake);
        Started started = oauth.start("google", full);
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(subject(), "intrus@gmail.com")));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");

        watch.sweep();
        expectOpen(connection);
    }
}
