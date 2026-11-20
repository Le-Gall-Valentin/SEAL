package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
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

/** AUTH-30 à AUTH-33 : retour OAuth, connexion d'un compte lié et inscription en attente (ADR 0032). */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthLoginIT {

    private static final AtomicLong GITHUB_IDS = new AtomicLong(7_000_000);

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

    @AfterEach
    void resetFake() {
        fake.reset();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String googleSubject() {
        return "1" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    private Registered active(String username) throws Exception {
        Registered account = client.register(username + "@exemple.ca", username);
        client.verify(account);
        return account;
    }

    private void link(Registered account, OAuthProvider provider, String subject) {
        identities.link(
                new UserId(account.id()),
                ExternalIdentity.of(provider, subject, account.email(), true, "Nom"),
                Instant.now());
    }

    private static String location(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        return result.getResponse().getHeader("Location");
    }

    private Session storedSession(Cookie cookie) {
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        Session session = sessions.findById(id);
        assertThat(session).as("session en base").isNotNull();
        return session;
    }

    @Test
    @DisplayName(
            "AUTH-30, AUTH-31 : Google ouvre la session complète du compte lié, sous un nouvel identifiant (AUTH-23)")
    void auth30GoogleSignsInTheLinkedAccount() throws Exception {
        Registered account = active(unique("g"));
        String subject = googleSubject();
        link(account, OAuthProvider.GOOGLE, subject);

        Started started = oauth.start("google");
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(subject, "autre@gmail.com")));

        assertThat(location(result)).isEqualTo("/");
        Cookie session = AuthClient.sessionOf(result);
        assertThat(session.getValue()).isNotEqualTo(started.session().getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.factors[0]").value("AUTHORIZATION_CODE"))
                .andExpect(jsonPath("$.factors.length()").value(1));
    }

    @Test
    @DisplayName("AUTH-30, AUTH-31 : GitHub ouvre la session complète du compte lié")
    void auth30GitHubSignsInTheLinkedAccount() throws Exception {
        Registered account = active(unique("h"));
        long id = GITHUB_IDS.incrementAndGet();
        link(account, OAuthProvider.GITHUB, Long.toString(id));

        MvcResult result = oauth.signIn("github", GitHubAccount.verified(id, "octo@exemple.ca"));

        assertThat(location(result)).isEqualTo("/");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(AuthClient.sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id().toString()));
    }

    @Test
    @DisplayName("AUTH-16 : un compte lié encore en attente obtient une session restreinte, renvoyée à la vérification")
    void auth16LinkedPendingAccountGetsARestrictedSession() throws Exception {
        String username = unique("p");
        Registered account = client.register(username + "@exemple.ca", username);
        long id = GITHUB_IDS.incrementAndGet();
        link(account, OAuthProvider.GITHUB, Long.toString(id));

        MvcResult result = oauth.signIn("github", GitHubAccount.verified(id, account.email()));

        assertThat(location(result)).isEqualTo("/verification-courriel");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(AuthClient.sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"));
    }

    @Test
    @DisplayName("AUTH-32 : premier passage, la session passe en attente, non authentifiée, sous un nouvel identifiant")
    void auth32FirstPassIsPending() throws Exception {
        Started started = oauth.start("google");
        MvcResult result = oauth.callback(
                started,
                fake.authorize(
                        started.location(), GoogleAccount.verified(googleSubject(), unique("n") + "@gmail.com")));

        assertThat(location(result)).isEqualTo("/inscription/identifiant");
        Cookie session = AuthClient.sessionOf(result);
        assertThat(session.getValue()).isNotEqualTo(started.session().getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session)).andExpect(status().isUnauthorized());
        Session stored = storedSession(session);
        assertThat(stored.getAttributeNames())
                .as("ni contexte authentifié, ni requête d'autorisation, ni jeton du fournisseur")
                .noneMatch(name -> name.contains("SECURITY_CONTEXT")
                        || name.contains("AuthorizationRequest")
                        || name.contains("AuthorizedClient"))
                .anyMatch(name -> name.contains("oauth.pending"));
    }

    @Test
    @DisplayName("AUTH-33 : le courriel vérifié d'un compte actif ne lie rien et n'ouvre rien : OAUTH_EMAIL_IN_USE")
    void auth33VerifiedEmailOfAnActiveAccount() throws Exception {
        Registered victim = active(unique("v"));
        String subject = googleSubject();

        Started started = oauth.start("google");
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(subject, victim.email())));

        assertThat(location(result)).isEqualTo("/connexion?erreur=OAUTH_EMAIL_IN_USE");
        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject)).isEmpty();
        Cookie renewed = result.getResponse().getCookie(AuthClient.SESSION_COOKIE);
        Cookie session = renewed != null ? renewed : started.session();
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session)).andExpect(status().isUnauthorized());
        assertThat(storedSession(session).getAttributeNames()).noneMatch(name -> name.contains("SECURITY_CONTEXT"));
    }

    @Test
    @DisplayName(
            "AUTH-31, ADR 0032 : nOAuth, le courriel non vérifié d'un compte actif mène à l'attente, jamais au compte")
    void auth31UnverifiedEmailOfAnActiveAccountGoesPending() throws Exception {
        Registered victim = active(unique("w"));

        MvcResult result = oauth.signIn("google", new GoogleAccount(googleSubject(), victim.email(), false, "Mallory"));

        assertThat(location(result)).isEqualTo("/inscription/identifiant");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(AuthClient.sessionOf(result)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUTH-30 : un code lié à un autre défi PKCE est refusé par le fournisseur : REQUEST_REJECTED")
    void auth30PkceMismatchIsRejected() throws Exception {
        Started started = oauth.start("google");
        String code = fake.authorizeWithOtherChallenge(
                started.location(), GoogleAccount.verified(googleSubject(), "pkce@gmail.com"));

        assertThat(location(oauth.callback(started, code))).isEqualTo("/connexion?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName("AUTH-30 : un jeton d'identité dont le nonce diffère est refusé : REQUEST_REJECTED")
    void auth30NonceMismatchIsRejected() throws Exception {
        fake.tweakIdTokens(claims -> claims.claim("nonce", "nonce-d-un-autre-depart"));

        MvcResult result = oauth.signIn("google", GoogleAccount.verified(googleSubject(), "nonce@gmail.com"));

        assertThat(location(result)).isEqualTo("/connexion?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName("AUTH-30 : le state d'un départ, rapporté dans une autre session, est refusé : REQUEST_REJECTED")
    void auth30StateFromAnotherSessionIsRejected() throws Exception {
        Started victim = oauth.start("google");
        Started attacker = oauth.start("google");
        String code = fake.authorize(victim.location(), GoogleAccount.verified(googleSubject(), "state@gmail.com"));

        MvcResult result = oauth.callback(victim, code, victim.state(), attacker.session());

        assertThat(location(result)).isEqualTo("/connexion?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName("AUTH-30 : un retour rejoué est refusé : REQUEST_REJECTED")
    void auth30ReplayedCallbackIsRejected() throws Exception {
        Registered account = active(unique("r"));
        String subject = googleSubject();
        link(account, OAuthProvider.GOOGLE, subject);
        Started started = oauth.start("google");
        String code = fake.authorize(started.location(), GoogleAccount.verified(subject, "r@gmail.com"));
        assertThat(location(oauth.callback(started, code))).isEqualTo("/");

        assertThat(location(oauth.callback(started, code))).isEqualTo("/connexion?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName("SEC-20 : connexion OAuth et refus journalisés, avec le fournisseur, sans courriel ni jeton (SEC-21)")
    void sec20OAuthLoginJournaled() throws Exception {
        Registered account = active(unique("j"));
        long id = GITHUB_IDS.incrementAndGet();
        link(account, OAuthProvider.GITHUB, Long.toString(id));
        oauth.signIn("github", GitHubAccount.verified(id, "journal@exemple.ca"));

        assertThat(jdbc.sql(
                                "SELECT details->>'provider' FROM security_events WHERE user_id = ? AND type = 'LOGIN_SUCCEEDED'")
                        .param(account.id())
                        .query(String.class)
                        .list())
                .contains("github");
        assertThat(jdbc.sql("SELECT details::text FROM security_events WHERE user_id = ?")
                        .param(account.id())
                        .query(String.class)
                        .list())
                .noneMatch(details -> details.contains("journal@exemple.ca"));
    }
}
