package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CreateOAuthAccountUseCase;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubAccount;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubEmail;
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;

/** AUTH-32 : choix de l'identifiant après un premier passage OAuth, puis création et liaison du compte (ADR 0032). */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthRegistrationIT {

    private static final AtomicLong GITHUB_IDS = new AtomicLong(8_000_000);

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
    private MutableClock clock;

    @Autowired
    private CreateOAuthAccountUseCase createOAuthAccount;

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
    void reset() {
        clock.reset();
        fake.reset();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String googleSubject() {
        return "2" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    /** Un premier passage Google : la session en attente. */
    private Cookie pendingGoogle(String subject, String email) throws Exception {
        MvcResult result = oauth.signIn("google", GoogleAccount.verified(subject, email));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/inscription/identifiant");
        return AuthClient.sessionOf(result);
    }

    private static Map<String, Object> choice(String username, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("displayName", displayName);
        return body;
    }

    private MvcResult complete(Cookie session, Map<String, Object> body) throws Exception {
        return mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, body))
                .andReturn();
    }

    private long events(UserId user, String type) {
        return jdbc.sql("SELECT count(*) FROM security_events WHERE user_id = ? AND type = ?")
                .param(user.value())
                .param(type)
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("AUTH-32 : GET /oauth/pending rend le fournisseur, le courriel, le nom proposé et l'expiration")
    void auth32PendingDescribesTheRegistration() throws Exception {
        String email = unique("p") + "@gmail.com";
        Cookie session = pendingGoogle(googleSubject(), email);

        mvc.perform(client.from(get("/api/v1/auth/oauth/pending")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("google"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Valentin Dupont"))
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    @DisplayName("AUTH-32 : courriel vérifié, le compte est créé actif, lié, et sa session complète ouverte")
    void auth32VerifiedEmailCreatesAnActiveAccount() throws Exception {
        String subject = googleSubject();
        String username = unique("oa");
        Cookie session = pendingGoogle(subject, username + "@gmail.com");

        MvcResult result = complete(session, choice(username, "Valentin"));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        Cookie opened = AuthClient.sessionOf(result);
        assertThat(opened.getValue()).isNotEqualTo(session.getValue());
        String openedId = new String(Base64.getDecoder().decode(opened.getValue()), StandardCharsets.UTF_8);
        Session stored = sessions.findById(openedId);
        assertThat(stored.getAttributeNames())
                .as("l'attente ne sert qu'une fois")
                .noneMatch(name -> name.contains("oauth.pending"));
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(opened))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value("Valentin"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.factors[0]").value("AUTHORIZATION_CODE"));
        UserId user = identities.findUser(OAuthProvider.GOOGLE, subject).orElseThrow();
        assertThat(events(user, "ACCOUNT_REGISTERED")).isEqualTo(1);
        assertThat(events(user, "OAUTH_LINKED")).isEqualTo(1);
        assertThat(oauth.signIn("google", GoogleAccount.verified(subject, "autre@gmail.com"))
                        .getResponse()
                        .getHeader("Location"))
                .as("le passage suivant connecte le compte créé")
                .isEqualTo("/");
    }

    @Test
    @DisplayName(
            "AUTH-32, ADR 0032 : courriel GitHub non vérifié, compte en attente, code SEAL envoyé, session restreinte")
    void auth32UnverifiedEmailCreatesAPendingAccount() throws Exception {
        long id = GITHUB_IDS.incrementAndGet();
        String username = unique("gh");
        String email = username + "@exemple.ca";
        MvcResult pending = oauth.signIn(
                "github", new GitHubAccount(id, "octo", null, List.of(new GitHubEmail(email, true, false))));
        Cookie session = AuthClient.sessionOf(pending);

        MvcResult result = complete(session, choice(username, "Octo"));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        Cookie restricted = AuthClient.sessionOf(result);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(restricted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"));
        Registered account = new Registered(
                identities
                        .findUser(OAuthProvider.GITHUB, Long.toString(id))
                        .orElseThrow()
                        .value(),
                email,
                username,
                restricted);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(client.verify(account)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("AUTH-32 : l'attente expire au bout de 10 minutes ; pending et complete répondent alors 404")
    void auth32PendingExpiresAfterTenMinutes() throws Exception {
        Cookie session = pendingGoogle(googleSubject(), unique("e") + "@gmail.com");
        clock.advance(Duration.ofMinutes(10).plusMillis(1));

        mvc.perform(client.from(get("/api/v1/auth/oauth/pending")).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(complete(session, choice(unique("e"), "Nom")).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("AUTH-32 : l'attente ne sert qu'une fois ; l'ancienne session ne crée pas un second compte")
    void auth32PendingIsSingleUse() throws Exception {
        String username = unique("s");
        Cookie session = pendingGoogle(googleSubject(), username + "@gmail.com");
        assertThat(complete(session, choice(username, "Nom")).getResponse().getStatus())
                .isEqualTo(204);

        MvcResult replay = complete(session, choice(unique("s"), "Nom"));

        assertThat(replay.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName(
            "AUTH-01 à AUTH-03 : identifiant invalide, réservé ou pris refusé ; l'attente reste et un bon choix passe")
    void auth01ToAuth03UsernameRules() throws Exception {
        Registered taken = new AuthClient(mvc, mail).register(unique("t") + "@exemple.ca", unique("t"));
        Cookie session = pendingGoogle(googleSubject(), unique("u") + "@gmail.com");

        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice("A!", "Nom")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_INVALID"));
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice("admin", "Nom")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USERNAME_RESERVED"));
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice(taken.username(), "Nom")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        assertThat(complete(session, choice(unique("u"), "Nom")).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("ACC-03 : un nom affiché vide ou avec un caractère de direction est refusé en VALIDATION_FAILED")
    void acc03DisplayNameRules() throws Exception {
        Cookie session = pendingGoogle(googleSubject(), unique("d") + "@gmail.com");

        for (String name : new String[] {"", "   ", "Vale\u202Entin", "x".repeat(51)}) {
            mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice(unique("d"), name)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName(
            "AUTH-33 : le courriel vérifié activé par un autre compte entre-temps donne OAUTH_EMAIL_IN_USE, sans rien créer")
    void auth33EmailActivatedMeanwhile() throws Exception {
        String username = unique("c");
        String email = username + "@exemple.ca";
        String subject = googleSubject();
        Cookie session = pendingGoogle(subject, email);
        AuthClient other = new AuthClient(mvc, mail);
        other.verify(other.register(email, unique("c")));

        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice(username, "Nom")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OAUTH_EMAIL_IN_USE"));
        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject)).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE username = ?")
                        .param(username)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("AUTH-32, A06 : deux complete simultanés de la même attente ne créent qu'un compte")
    void a06ConcurrentCompletesCreateOneAccount() throws Exception {
        long id = GITHUB_IDS.incrementAndGet();
        String email = unique("r") + "@exemple.ca";
        Cookie session = AuthClient.sessionOf(oauth.signIn(
                "github", new GitHubAccount(id, "octo", null, List.of(new GitHubEmail(email, true, false)))));
        String token = client.csrfToken(session);

        List<Integer> statuses = Race.run(
                2,
                index -> mvc.perform(client.from(post("/api/v1/auth/oauth/complete"))
                                .cookie(session, new Cookie(AuthClient.CSRF_COOKIE, token))
                                .header(AuthClient.CSRF_HEADER, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"" + unique("r") + "\",\"displayName\":\"Nom\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses).containsOnlyOnce(204);
        assertThat(statuses).anyMatch(status -> status == 404 || status == 409);
        assertThat(identities.findUser(OAuthProvider.GITHUB, Long.toString(id))).isPresent();
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = CAST(? AS citext)")
                        .param(email)
                        .query(Long.class)
                        .single())
                .as("un seul compte, même en attente : l'index des courriels vérifiés ne protège pas ce cas")
                .isOne();
    }

    @Test
    @DisplayName(
            "QA-05 : sans attente 404 ; session ouverte 403 ; sans jeton CSRF 403 ; champ inconnu ou trop long 400")
    void qa05AccessRules() throws Exception {
        mvc.perform(client.from(get("/api/v1/auth/oauth/pending")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), null, choice(unique("q"), "Nom")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        AuthClient other = new AuthClient(mvc, mail);
        Cookie full = other.verify(other.register(unique("q") + "@exemple.ca", unique("q")));
        mvc.perform(other.from(get("/api/v1/auth/oauth/pending")).cookie(full))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(other.unsafe(post("/api/v1/auth/oauth/complete"), full, choice(unique("q"), "Nom")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Cookie session = pendingGoogle(googleSubject(), unique("q") + "@gmail.com");
        mvc.perform(client.from(post("/api/v1/auth/oauth/complete"))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + unique("q") + "\",\"displayName\":\"Nom\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        Map<String, Object> unknown = choice(unique("q"), "Nom");
        unknown.put("email", "autre@exemple.ca");
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice("u".repeat(65), "Nom")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), session, choice(unique("q"), "n".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("SEC-10, ADR 0032 : complete compte dans la limite d'inscription, 5 par heure et par IP")
    void sec10CompleteCountsAsRegistration() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(complete(null, choice(unique("l"), "Nom")).getResponse().getStatus())
                    .isEqualTo(404);
        }

        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), null, choice(unique("l"), "Nom")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName(
            "AUTH-70, AUTH-21 : un compte créé par OAuth n'a pas de mot de passe ; ni connexion ni lien de réinitialisation")
    void auth70OAuthAccountHasNoPassword() throws Exception {
        String username = unique("np");
        String email = username + "@gmail.com";
        Cookie session = pendingGoogle(googleSubject(), email);
        assertThat(complete(session, choice(username, "Nom")).getResponse().getStatus())
                .isEqualTo(204);

        AuthClient other = new AuthClient(mvc, mail);
        mvc.perform(other.unsafe(
                        post("/api/v1/auth/login"), null, Map.of("login", username, "password", AuthClient.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(other.unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", email)))
                .andExpect(status().isNoContent());
        assertThat(mail.sentTo(email))
                .as("aucun lien pour un compte sans mot de passe")
                .isEmpty();
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0032 (point 8) : après complete, le facteur est daté du retour du fournisseur, pas de la création")
    void adr0032FactorDatedFromTheProviderReturn() throws Exception {
        String username = unique("f");
        Cookie session = pendingGoogle(googleSubject(), username + "@gmail.com");
        Instant returned = clock.instant();
        clock.advance(Duration.ofMinutes(5));

        MvcResult result = complete(session, choice(username, "Nom"));

        Cookie opened = AuthClient.sessionOf(result);
        String openedId = new String(Base64.getDecoder().decode(opened.getValue()), StandardCharsets.UTF_8);
        Session stored = sessions.findById(openedId);
        SecurityContext context = stored.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .satisfies(factor -> assertThat(((FactorGrantedAuthority) factor).getIssuedAt())
                        .isBetween(returned.minusSeconds(1), returned.plusSeconds(60)));
    }

    @Test
    @DisplayName("AUTH-32 : le compte d'une inscription OAuth ne se crée que dans la transaction qui le lie")
    void auth32AccountCreationRequiresTheLinkingTransaction() {
        assertThatThrownBy(() -> createOAuthAccount.create(unique("t"), "Nom", unique("t") + "@exemple.ca", true))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("AUTH-31, AUTH-32 : identité liée entre deux attentes, la seconde est refusée et n'en laisse rien")
    void auth31SecondPendingOfALinkedIdentityLeavesNothing() throws Exception {
        long id = GITHUB_IDS.incrementAndGet();
        String firstName = unique("pa");
        String secondName = unique("pb");
        Cookie first = AuthClient.sessionOf(oauth.signIn(
                "github",
                new GitHubAccount(id, "octo", null, List.of(new GitHubEmail(firstName + "@exemple.ca", true, false)))));
        Cookie second = AuthClient.sessionOf(oauth.signIn(
                "github",
                new GitHubAccount(
                        id, "octo", null, List.of(new GitHubEmail(secondName + "@exemple.ca", true, false)))));
        assertThat(complete(first, choice(firstName, "Premier")).getResponse().getStatus())
                .isEqualTo(204);

        mvc.perform(client.unsafe(post("/api/v1/auth/oauth/complete"), second, choice(secondName, "Second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OAUTH_ALREADY_LINKED"));

        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE username = ?")
                        .param(secondName)
                        .query(Long.class)
                        .single())
                .as("le second compte est annulé avec la liaison")
                .isZero();
        assertThat(mail.sentTo(secondName + "@exemple.ca"))
                .as("aucun code pour un compte annulé")
                .isEmpty();
    }
}
