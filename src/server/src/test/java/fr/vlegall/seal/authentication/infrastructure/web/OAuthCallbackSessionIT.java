package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubAccount;
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthFlow.Started;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AUTH-30, ADR 0032 (point 7) : le retour OAuth n'est accepté que par une session anonyme et pour le fournisseur du
 * départ ; une session qui s'ouvre ne garde ni requête d'autorisation ni inscription en attente.
 */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthCallbackSessionIT {

    private static final String REJECTED = "/connexion?erreur=REQUEST_REJECTED";
    private static final AtomicLong GITHUB_IDS = new AtomicLong(9_500_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    @Autowired
    private OAuthIdentityRepository identities;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JdbcIndexedSessionRepository sessions;

    private AuthClient client;
    private OAuthFlow oauth;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
        oauth = new OAuthFlow(mvc, client, fake);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String googleSubject() {
        return "4" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    private Registered active(String username) throws Exception {
        AuthClient other = new AuthClient(mvc, mail);
        Registered account = other.register(username + "@exemple.ca", username);
        other.verify(account);
        return account;
    }

    /** Connexion par mot de passe dans la session donnée : la session ouverte, sous un nouvel identifiant. */
    private Cookie passwordLogin(Cookie session, Registered account) throws Exception {
        MvcResult login = mvc.perform(client.unsafe(
                        post("/api/v1/auth/login"),
                        session,
                        Map.of("login", account.username(), "password", AuthClient.PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return AuthClient.sessionOf(login);
    }

    private Session stored(Cookie cookie) {
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        Session session = sessions.findById(id);
        assertThat(session).as("session en base").isNotNull();
        return session;
    }

    private static Cookie after(MvcResult result, Cookie before) {
        Cookie renewed = result.getResponse().getCookie(AuthClient.SESSION_COOKIE);
        return renewed != null ? renewed : before;
    }

    @Test
    @DisplayName("AUTH-30 : un retour reçu par une session ouverte est refusé et ne change pas son compte")
    void auth30CallbackOnASignedInSessionIsRejected() throws Exception {
        Registered alice = active(unique("al"));
        Registered bob = active(unique("bo"));
        String bobSubject = googleSubject();
        identities.link(
                new UserId(bob.id()),
                ExternalIdentity.of(OAuthProvider.GOOGLE, bobSubject, bob.email(), true, "Bob"),
                Instant.now());
        Started started = oauth.start("google");
        Cookie aliceSession = passwordLogin(started.session(), alice);

        MvcResult callback = oauth.callback(
                started,
                fake.authorize(started.location(), GoogleAccount.verified(bobSubject, "bob@gmail.com")),
                started.state(),
                aliceSession);

        assertThat(callback.getResponse().getHeader("Location")).isEqualTo(REJECTED);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(after(callback, aliceSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.id().toString()))
                .andExpect(jsonPath("$.factors[0]").value("PASSWORD"));
    }

    @Test
    @DisplayName("AUTH-30 : un premier passage reçu par une session ouverte ne la déconnecte pas pour une attente")
    void auth30FirstPassOnASignedInSessionIsRejected() throws Exception {
        Registered alice = active(unique("am"));
        Started started = oauth.start("github");
        Cookie aliceSession = passwordLogin(started.session(), alice);
        long id = GITHUB_IDS.incrementAndGet();

        MvcResult callback = oauth.callback(
                started,
                fake.authorize(started.location(), GitHubAccount.verified(id, unique("m") + "@exemple.ca")),
                started.state(),
                aliceSession);

        assertThat(callback.getResponse().getHeader("Location")).isEqualTo(REJECTED);
        Cookie session = after(callback, aliceSession);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andExpect(jsonPath("$.id").value(alice.id().toString()));
        assertThat(stored(session).getAttributeNames()).noneMatch(name -> name.contains("oauth.pending"));
    }

    @Test
    @DisplayName(
            "AUTH-30, RFC 9700 : le retour d'un départ Google rapporté sur l'adresse de GitHub est refusé (mix-up)")
    void auth30ProviderMixUpIsRejected() throws Exception {
        Started google = oauth.start("google");
        String code = fake.authorize(google.location(), GoogleAccount.verified(googleSubject(), "mix@gmail.com"));
        Started onGitHub = new Started("github", google.session(), google.location());

        MvcResult callback = oauth.callback(onGitHub, code);

        assertThat(callback.getResponse().getHeader("Location")).isEqualTo(REJECTED);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(after(callback, google.session())))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT details->>'provider' || ':' || (details->>'cause') FROM security_events "
                                + "WHERE type = 'LOGIN_FAILED' AND ip = CAST(? AS inet)")
                        .param(client.ip())
                        .query(String.class)
                        .list())
                .as("le refus est attribué au fournisseur du départ")
                .containsExactly("google:provider_mismatch");
    }

    @Test
    @DisplayName(
            "AUTH-23, ADR 0032 : une session qui s'ouvre ne garde ni requête d'autorisation ni inscription en attente")
    void auth23OpeningASessionDropsOAuthState() throws Exception {
        MvcResult pending = oauth.signIn("google", GoogleAccount.verified(googleSubject(), unique("p") + "@gmail.com"));
        Cookie pendingSession = AuthClient.sessionOf(pending);
        MvcResult secondStart = mvc.perform(
                        client.from(get("/oauth2/authorization/github")).cookie(pendingSession))
                .andExpect(status().isFound())
                .andReturn();
        Cookie both = after(secondStart, pendingSession);
        assertThat(stored(both).getAttributeNames())
                .as("la session porte une attente et une requête d'autorisation")
                .anyMatch(name -> name.contains("oauth.pending"))
                .anyMatch(name -> name.contains("AUTHORIZATION_REQUEST"));

        Cookie opened = passwordLogin(both, active(unique("op")));

        assertThat(stored(opened).getAttributeNames())
                .as("ni attente ni requête d'autorisation dans la session ouverte")
                .noneMatch(name -> name.contains("oauth.pending") || name.contains("AUTHORIZATION_REQUEST"));
    }

    @Test
    @DisplayName("SEC-20 : un code d'erreur du fournisseur hors de la RFC 6749 est journalisé oauth_error")
    void sec20UnknownProviderErrorCodeIsNotJournaledAsIs() throws Exception {
        Started started = oauth.start("google");

        mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("error", "account_unusable")
                                .param("state", started.state()))
                        .cookie(started.session()))
                .andExpect(status().isFound());

        assertThat(jdbc.sql("SELECT details->>'cause' FROM security_events WHERE type = 'LOGIN_FAILED' "
                                + "AND ip = CAST(? AS inet)")
                        .param(client.ip())
                        .query(String.class)
                        .list())
                .containsExactly("oauth_error");
    }
}
