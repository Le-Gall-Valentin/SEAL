package fr.vlegall.seal.authentication.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.PasswordHashingProbe;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Mot de passe oublié, de bout en bout dans le contexte complet (AUTH-70, AUTH-71, ADR 0022). */
@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetIT {

    private static final String REQUEST = "/api/v1/auth/password-reset/request";
    private static final String INSPECT = "/api/v1/auth/password-reset/inspect";
    private static final String CONFIRM = "/api/v1/auth/password-reset/confirm";
    private static final String NEW_PASSWORD = "nouvelle-phrase-de-passe";
    private static final Pattern LINK =
            Pattern.compile("(https://\\S+/mot-de-passe/nouveau#jeton=)([A-Za-z0-9_-]{43})\\.");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PasswordHashingProbe hashing;

    @Autowired
    private SealProperties seal;

    private AuthClient client;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private Registered active(String username) throws Exception {
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register(username + "@exemple.ca", username);
        owner.verify(account);
        mail.reset();
        return account;
    }

    private MvcResult request(AuthClient from, String email) throws Exception {
        return mvc.perform(from.unsafe(post(REQUEST), null, Map.of("email", email)))
                .andReturn();
    }

    private MvcResult inspect(String token) throws Exception {
        return mvc.perform(client.unsafe(post(INSPECT), null, Map.of("token", token)))
                .andReturn();
    }

    private MvcResult confirm(AuthClient from, String token, String password) throws Exception {
        return mvc.perform(from.unsafe(post(CONFIRM), null, Map.of("token", token, "password", password)))
                .andReturn();
    }

    private MvcResult login(String login, String password) throws Exception {
        return mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/login"), null, Map.of("login", login, "password", password)))
                .andReturn();
    }

    /** Jeton du dernier lien envoyé à cette adresse. */
    private String tokenSentTo(String email) {
        List<SimpleMailMessage> sent = mail.sentTo(email);
        assertThat(sent).as("courriel envoyé à " + email).isNotEmpty();
        Matcher link = LINK.matcher(sent.getLast().getText());
        assertThat(link.find()).as("lien dans le courriel").isTrue();
        return link.group(2);
    }

    private String requestToken(Registered account) throws Exception {
        assertThat(request(client, account.email()).getResponse().getStatus()).isEqualTo(204);
        return tokenSentTo(account.email());
    }

    @Test
    @DisplayName("AUTH-70 : même réponse pour un compte actif, inconnu, en attente ou sans mot de passe (ADR 0022)")
    void auth70ResponseIsIdenticalWhateverTheAccount() throws Exception {
        Registered activeAccount = active(unique("actif"));
        Registered pendingAccount =
                new AuthClient(mvc, mail).register(unique("attente") + "@exemple.ca", unique("att"));
        String passwordless = unique("oauth") + "@exemple.ca";
        jdbc.sql("INSERT INTO users (username, display_name, email, status) VALUES (?, 'OAuth', ?, 'ACTIVE')")
                .param(unique("oauth"))
                .param(passwordless)
                .update();
        mail.reset();

        List<MvcResult> results = new ArrayList<>();
        for (String email : List.of(
                activeAccount.email(), unique("inconnu") + "@exemple.ca", pendingAccount.email(), passwordless)) {
            results.add(request(new AuthClient(mvc, mail), email));
        }

        assertThat(results).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertThat(result.getResponse().getContentAsString()).isEmpty();
        });
        assertThat(mail.sent())
                .singleElement()
                .satisfies(sent -> assertThat(sent.getTo()).containsExactly(activeAccount.email()));
    }

    @Test
    @DisplayName("AUTH-70 : le courriel porte le lien de l'écran Nouveau-Mot-de-passe, jeton dans le fragment")
    void auth70MailCarriesTheLinkInItsFragment() throws Exception {
        Registered account = active(unique("lien"));

        String token = requestToken(account);

        SimpleMailMessage sent = mail.sentTo(account.email()).getLast();
        assertThat(sent.getSubject()).isEqualTo("Réinitialisation de votre mot de passe SEAL");
        assertThat(sent.getText())
                .isEqualTo("Pour choisir un nouveau mot de passe pour @" + account.username() + ", ouvrez ce lien : "
                        + seal.origin() + "/mot-de-passe/nouveau#jeton=" + token
                        + ". Il est valable 30 minutes et ne sert qu'une fois. Vos clés ne changent pas. Si vous"
                        + " n'avez rien demandé, ignorez ce message : votre mot de passe reste inchangé.");
        assertThat(jdbc.sql("SELECT count(*) FROM password_reset_tokens WHERE encode(token_hash, 'escape') LIKE ?")
                        .param("%" + token + "%")
                        .query(Long.class)
                        .single())
                .as("le jeton n'est jamais stocké en clair")
                .isZero();
    }

    @Test
    @DisplayName("SEC-10 : 3 demandes par heure et par courriel saisi, quelle que soit l'IP")
    void sec10ThreeRequestsPerHourPerEmail() throws Exception {
        String email = unique("limite") + "@exemple.ca";
        for (int i = 0; i < 3; i++) {
            assertThat(request(new AuthClient(mvc, mail), email).getResponse().getStatus())
                    .isEqualTo(204);
        }

        mvc.perform(new AuthClient(mvc, mail).unsafe(post(REQUEST), null, Map.of("email", email.toUpperCase())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("SEC-10 : 3 demandes par heure et par IP ; un refus par courriel n'entame pas l'IP (ADR 0022)")
    void sec10ThreeRequestsPerHourPerIp() throws Exception {
        String exhausted = unique("epuise") + "@exemple.ca";
        for (int i = 0; i < 3; i++) {
            request(new AuthClient(mvc, mail), exhausted);
        }
        assertThat(request(client, exhausted).getResponse().getStatus()).isEqualTo(429);

        for (int i = 0; i < 3; i++) {
            assertThat(request(client, unique("ip") + "@exemple.ca")
                            .getResponse()
                            .getStatus())
                    .isEqualTo(204);
        }
        assertThat(request(client, unique("ip") + "@exemple.ca").getResponse().getStatus())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("AUTH-70 : un lien valable donne l'identifiant, le courriel et l'expiration ; expiré, 422 (ADR 0022)")
    void auth70InspectGivesTheAccountUntilExpiry() throws Exception {
        Registered account = active(unique("lecture"));
        Instant before = clock.instant();
        String token = requestToken(account);
        Instant after = clock.instant();

        MvcResult result = mvc.perform(client.unsafe(post(INSPECT), null, Map.of("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(account.username()))
                .andExpect(jsonPath("$.email").value(account.email()))
                .andReturn();
        Instant expiresAt =
                Instant.parse(AuthClient.body(result).get("expiresAt").asString());
        assertThat(expiresAt)
                .isBetween(
                        before.plus(Duration.ofMinutes(30)).minusMillis(1),
                        after.plus(Duration.ofMinutes(30)).plusMillis(1));

        clock.advance(Duration.ofMinutes(30));
        mvc.perform(client.unsafe(post(INSPECT), null, Map.of("token", token)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("AUTH-70 : le nouveau mot de passe remplace l'ancien et toutes les sessions tombent")
    void auth70ResetReplacesPasswordAndRevokesEverySession() throws Exception {
        Registered account = active(unique("reinit"));
        Cookie first = sessionOf(login(account.username(), PASSWORD));
        Cookie second = sessionOf(login(account.email(), PASSWORD));
        String token = requestToken(account);

        assertThat(confirm(client, token, NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);

        for (Cookie session : List.of(first, second)) {
            mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session)).andExpect(status().isUnauthorized());
        }
        assertThat(login(account.username(), PASSWORD).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(login(account.username(), NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("AUTH-70 : un lien ne sert qu'une fois ; le second usage répond 422 CODE_EXPIRED")
    void auth70LinkIsSingleUse() throws Exception {
        Registered account = active(unique("unique"));
        String token = requestToken(account);
        confirm(client, token, NEW_PASSWORD);

        mvc.perform(client.unsafe(post(CONFIRM), null, Map.of("token", token, "password", "autre-phrase-de-passe")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertThat(login(account.username(), NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("AUTH-70 : une nouvelle demande invalide le lien précédent (ADR 0022)")
    void auth70NewRequestInvalidatesThePreviousLink() throws Exception {
        Registered account = active(unique("double"));
        String first = requestToken(account);
        String second = requestToken(account);

        mvc.perform(client.unsafe(post(CONFIRM), null, Map.of("token", first, "password", NEW_PASSWORD)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertThat(confirm(client, second, NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe refusé répond 422 PASSWORD_TOO_WEAK et garde le lien (ADR 0022)")
    void auth11WeakPasswordKeepsTheLink() throws Exception {
        Registered account = active(unique("faible"));
        String token = requestToken(account);

        mvc.perform(client.unsafe(post(CONFIRM), null, Map.of("token", token, "password", "trop-court")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
        mvc.perform(client.unsafe(
                        post(CONFIRM),
                        null,
                        Map.of("token", token, "password", account.username().repeat(3))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));

        assertThat(confirm(client, token, NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("AUTH-70 : deux confirmations simultanées du même lien : une seule réussit")
    void auth70ConcurrentConfirmationsSucceedOnce() throws Exception {
        Registered account = active(unique("course"));
        String token = requestToken(account);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String password : List.of(NEW_PASSWORD, "autre-phrase-de-passe")) {
                AuthClient from = new AuthClient(mvc, mail);
                var prepared = from.unsafe(post(CONFIRM), null, Map.of("token", token, "password", password));
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(prepared).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            assertThat(List.of(statuses.get(0).get(), statuses.get(1).get())).containsExactlyInAnyOrder(204, 422);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "AUTH-13 : la réinitialisation hache sans tenir aucune connexion à la base ; un lien faux ne hache pas")
    void auth13ResetHashesWithoutHoldingAConnection() throws Exception {
        Registered account = active(unique("pool"));
        String token = requestToken(account);
        hashing.reset();

        confirm(client, "A".repeat(43), NEW_PASSWORD);
        assertThat(hashing.observations())
                .as("un lien inconnu ne coûte aucun calcul")
                .isEmpty();

        confirm(client, token, NEW_PASSWORD);
        assertThat(hashing.observations()).containsExactly(new PasswordHashingProbe.Observation(false, 0));
    }

    @Test
    @DisplayName("AUTH-71 : la réinitialisation ne touche à aucune table de clés")
    void auth71KeysAreUntouched() throws Exception {
        Registered account = active(unique("cles"));
        String before = keyTablesFingerprint();

        confirm(client, requestToken(account), NEW_PASSWORD);

        assertThat(keyTablesFingerprint()).isEqualTo(before);
    }

    private String keyTablesFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        for (String table :
                List.of("identity_certificates", "subkeys", "key_vaults", "recovery_codes", "key_challenges")) {
            fingerprint
                    .append(table)
                    .append('=')
                    .append(jdbc.sql("SELECT coalesce(md5(string_agg(t::text, ',' ORDER BY t::text)), '') FROM " + table
                                    + " t")
                            .query(String.class)
                            .single())
                    .append(';');
        }
        return fingerprint.toString();
    }

    @Test
    @DisplayName("QA-05 : les trois routes exigent un jeton CSRF et refusent toute session (403, ADR 0022)")
    void qa05RoutesNeedCsrfAndRefuseSessions() throws Exception {
        Registered pendingAccount = new AuthClient(mvc, mail).register(unique("att") + "@exemple.ca", unique("att"));
        Registered full = active(unique("plein"));
        Cookie fullSession = sessionOf(login(full.username(), PASSWORD));
        Map<String, Map<String, String>> bodies = Map.of(
                REQUEST, Map.of("email", "x@exemple.ca"),
                INSPECT, Map.of("token", "A".repeat(43)),
                CONFIRM, Map.of("token", "A".repeat(43), "password", NEW_PASSWORD));

        for (Map.Entry<String, Map<String, String>> route : bodies.entrySet()) {
            mvc.perform(client.from(post(route.getKey()))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isForbidden());
            for (Cookie session : List.of(pendingAccount.session(), fullSession)) {
                mvc.perform(client.unsafe(post(route.getKey()), session, route.getValue()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
    }

    @Test
    @DisplayName("SEC-01 : champ inconnu, trop long ou jeton mal formé : 400 VALIDATION_FAILED, sans calcul")
    void sec01InvalidBodiesAreRejected() throws Exception {
        hashing.reset();
        Map<String, List<Map<String, ?>>> bodies = Map.of(
                REQUEST,
                List.of(
                        Map.of("email", "x@exemple.ca", "username", "x"),
                        Map.of("email", "x".repeat(250) + "@e.ca"),
                        Map.of()),
                INSPECT,
                List.of(
                        Map.of("token", "A".repeat(42)),
                        Map.of("token", "A".repeat(42) + "+"),
                        Map.of("token", "A".repeat(43), "extra", 1)),
                CONFIRM,
                List.of(
                        Map.of("token", "A".repeat(44), "password", NEW_PASSWORD),
                        Map.of("token", "A".repeat(43), "password", "p".repeat(513)),
                        Map.of("token", "A".repeat(43))));

        for (Map.Entry<String, List<Map<String, ?>>> route : bodies.entrySet()) {
            for (Map<String, ?> body : route.getValue()) {
                mvc.perform(client.unsafe(post(route.getKey()), null, body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            }
        }
        assertThat(hashing.observations()).isEmpty();
    }

    @Test
    @DisplayName("SEC-20, SEC-21 : PASSWORD_RESET et SESSION_REVOKED sont journalisés, sans jeton ni mot de passe")
    void sec20ResetIsJournaledWithoutSecrets(CapturedOutput output) throws Exception {
        Registered account = active(unique("journal"));
        login(account.username(), PASSWORD);
        String token = requestToken(account);

        confirm(client, token, NEW_PASSWORD);

        List<String> types = jdbc.sql("SELECT type FROM security_events WHERE user_id = ? AND type IN"
                        + " ('PASSWORD_RESET', 'SESSION_REVOKED') ORDER BY created_at, id")
                .param(account.id())
                .query(String.class)
                .list();
        assertThat(types).containsExactly("SESSION_REVOKED", "PASSWORD_RESET");
        for (String secret : List.of(token, NEW_PASSWORD)) {
            assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE details::text LIKE ?")
                            .param("%" + secret + "%")
                            .query(Long.class)
                            .single())
                    .isZero();
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }
}
