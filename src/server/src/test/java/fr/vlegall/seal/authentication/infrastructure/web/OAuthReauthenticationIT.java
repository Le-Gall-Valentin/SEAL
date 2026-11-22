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
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthFlow.Started;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-55, ADR 0034 (point 2) : mode sudo par un nouvel aller-retour OAuth, de bout en bout avec le faux fournisseur. */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthReauthenticationIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    @Autowired
    private OAuthIdentityRepository identities;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

    private AuthClient client;
    private OAuthFlow oauth;

    /** Suffixe propre à ce test : la base est partagée par toute la série des tests d'intégration. */
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
        oauth = new OAuthFlow(mvc, client, fake);
    }

    @AfterEach
    void reset() {
        fake.reset();
        mail.reset();
        clock.reset();
    }

    private static String googleSubject() {
        return "1" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    /** Compte actif dont la session complète a plus de 10 minutes, lié à ce sujet Google. */
    private Cookie staleSessionLinkedTo(String username, String subject) throws Exception {
        Registered account = client.register(username + run + "@exemple.ca", username + run);
        Cookie full = client.verify(account);
        identities.link(
                new UserId(account.id()),
                ExternalIdentity.of(OAuthProvider.GOOGLE, subject, account.email(), true, "Nom"),
                Instant.now());
        clock.advance(Duration.ofMinutes(11));
        return full;
    }

    private int emailChangeStatus(Cookie session) throws Exception {
        return mvc.perform(client.unsafe(
                        post("/api/v1/account/email"), session, Map.of("email", "neuve" + run + "@exemple.ca")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<String> causes(UUID user) {
        return jdbc.sql("SELECT details->>'cause' FROM security_events WHERE user_id = :user AND type = 'LOGIN_FAILED'")
                .param("user", user)
                .query(String.class)
                .list();
    }

    private UUID idOf(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username = :username")
                .param("username", username + run)
                .query(UUID.class)
                .single();
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0034 : la session complète part avec prompt=select_account ; l'identité liée redate le facteur et mène à /parametres")
    void auth55LinkedIdentityReopensSudo() throws Exception {
        String subject = googleSubject();
        Cookie stale = staleSessionLinkedTo("oauthsudo", subject);
        assertThat(emailChangeStatus(stale)).isEqualTo(403);

        Started started = oauth.start("google", stale);
        assertThat(OAuthFlow.query(started.location())).containsEntry("prompt", "select_account");
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(subject, "autre@gmail.com")));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres");
        Cookie renewed = AuthClient.sessionOf(result);
        assertThat(renewed.getValue()).isNotEqualTo(stale.getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(renewed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("oauthsudo" + run))
                .andExpect(jsonPath("$.factors.length()").value(2));
        assertThat(emailChangeStatus(renewed)).isEqualTo(204);
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : une autre identité du même fournisseur est refusée ; la session ne change pas")
    void auth55OtherIdentityIsRefused() throws Exception {
        Cookie stale = staleSessionLinkedTo("autreid", googleSubject());

        Started started = oauth.start("google", stale);
        MvcResult result = oauth.callback(
                started,
                fake.authorize(started.location(), GoogleAccount.verified(googleSubject(), "intrus@gmail.com")));

        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");
        // Spring change l'identifiant de session dès le retour du fournisseur : le navigateur suit le nouveau cookie.
        Cookie after = result.getResponse().getCookie(AuthClient.SESSION_COOKIE) == null
                ? stale
                : AuthClient.sessionOf(result);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(after))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("autreid" + run));
        assertThat(emailChangeStatus(after)).as("toujours hors du mode sudo").isEqualTo(403);
        assertThat(causes(idOf("autreid"))).contains("reauth_identity_mismatch");
    }

    @Test
    @DisplayName("AUTH-55 : un refus chez le fournisseur ramène aux paramètres, pas à la connexion")
    void auth55ProviderRefusalLeadsBackToSettings() throws Exception {
        Cookie stale = staleSessionLinkedTo("refus", googleSubject());

        Started started = oauth.start("google", stale);
        MvcResult result = mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("error", "access_denied")
                                .param("state", started.state()))
                        .cookie(stale))
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(stale)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADR 0032, ADR 0034 : le retour d'une ré-authentification n'est accepté par aucune autre session")
    void adr0034ReturnBelongsToTheStartingSession() throws Exception {
        String subject = googleSubject();
        Cookie stale = staleSessionLinkedTo("lieesess", subject);
        Started started = oauth.start("google", stale);
        String code = fake.authorize(started.location(), GoogleAccount.verified(subject, "autre@gmail.com"));

        MvcResult anonymous = mvc.perform(client.from(
                        get("/login/oauth2/code/google").param("code", code).param("state", started.state())))
                .andReturn();

        assertThat(anonymous.getResponse().getHeader("Location")).isEqualTo("/connexion?erreur=REQUEST_REJECTED");
        assertThat(emailChangeStatus(stale)).isEqualTo(403);
    }

    @Test
    @DisplayName("AUTH-16 : une session restreinte ne part pas en ré-authentification")
    void auth16RestrictedSessionCannotStart() throws Exception {
        Registered pending = client.register("attente" + run + "@exemple.ca", "attente" + run);

        mvc.perform(client.from(get("/oauth2/authorization/google")).cookie(pending.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUTH-30 : un départ anonyme reste une connexion, sans prompt")
    void auth30AnonymousStartHasNoPrompt() throws Exception {
        Started started = oauth.start("google");

        assertThat(OAuthFlow.query(started.location())).doesNotContainKey("prompt");
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0034 : au-delà de 20 départs par 15 minutes, la ré-authentification revient aux paramètres avec RATE_LIMITED")
    void sec10RateLimitedReauthenticationLeadsBackToSettings() throws Exception {
        Cookie stale = staleSessionLinkedTo("limiteoa", googleSubject());
        for (int i = 0; i < 20; i++) {
            oauth.start("google", stale);
        }

        MvcResult refused = mvc.perform(
                        client.from(get("/oauth2/authorization/google")).cookie(stale))
                .andReturn();

        assertThat(refused.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=RATE_LIMITED");
        assertThat(refused.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    @DisplayName("AUTH-55 : l'identité Google liée à un autre compte n'ouvre pas le sudo de cette session")
    void auth55IdentityOfAnotherAccountIsRefused() throws Exception {
        String other = googleSubject();
        staleSessionLinkedTo("autrecpt", other);
        Cookie stale = staleSessionLinkedTo("victime", googleSubject());

        Started started = oauth.start("google", stale);
        MvcResult result = oauth.callback(
                started, fake.authorize(started.location(), GoogleAccount.verified(other, "autre@gmail.com")));

        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");
        Cookie after = result.getResponse().getCookie(AuthClient.SESSION_COOKIE) == null
                ? stale
                : AuthClient.sessionOf(result);
        assertThat(emailChangeStatus(after)).isEqualTo(403);
        assertThat(causes(idOf("victime"))).contains("reauth_identity_mismatch");
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0034 : un départ vers un fournisseur que le compte n'a pas lié revient aux paramètres sans"
                    + " aller-retour")
    void adr0034StartToAnUnlinkedProviderGoesBackToSettings() throws Exception {
        Cookie stale = staleSessionLinkedTo("nonlie", googleSubject());

        MvcResult result = mvc.perform(
                        client.from(get("/oauth2/authorization/github")).cookie(stale))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName(
            "OPS-03 : un fournisseur inconnu reste introuvable pour l'anonyme, et ramène la session complète aux paramètres")
    void ops03UnknownProvider() throws Exception {
        mvc.perform(client.from(get("/oauth2/authorization/inconnu")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Cookie stale = staleSessionLinkedTo("inconnu", googleSubject());
        MvcResult result = mvc.perform(
                        client.from(get("/oauth2/authorization/inconnu")).cookie(stale))
                .andReturn();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/parametres?erreur=REQUEST_REJECTED");
    }
}
