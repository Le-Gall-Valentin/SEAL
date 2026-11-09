package fr.vlegall.seal.identity.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.CSRF_COOKIE;
import static fr.vlegall.seal.support.AuthClient.CSRF_HEADER;
import static fr.vlegall.seal.support.AuthClient.body;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Inscription, disponibilité de l'identifiant et compte courant, de bout en bout dans le contexte complet. */
@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RegistrationIT {

    private static final JsonMapper JSON = JsonMapper.shared();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    private AuthClient client;

    /** Une adresse IP par test : les seaux de limitation de débit vivent le temps du contexte. */
    private String ip;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
        ip = client.ip();
    }

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    // ---- outils ----

    /** Adresse IP d'un autre client, pour une seconde inscription dans le même test. */
    private String otherIp() {
        return new AuthClient(mvc, mail).ip();
    }

    MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String address) {
        return request.secure(true).with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }

    String csrfToken() throws Exception {
        return client.csrfToken(null);
    }

    static String json(Map<String, ?> body) {
        return JSON.writeValueAsString(body);
    }

    static Map<String, Object> registration(String email, String username) {
        return AuthClient.registration(email, username);
    }

    MockHttpServletRequestBuilder registerRequest(Map<String, ?> body, String address) throws Exception {
        String token = csrfToken();
        return from(post("/api/v1/auth/register"), address)
                .cookie(new Cookie(CSRF_COOKIE, token))
                .header(CSRF_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    MvcResult register(String email, String username) throws Exception {
        return mvc.perform(registerRequest(registration(email, username), ip))
                .andExpect(status().isCreated())
                .andReturn();
    }

    String codeSentTo(String address) {
        return client.codeSentTo(address);
    }

    long count(String sql, Object... params) {
        return jdbc.sql(sql).params(List.of(params)).query(Long.class).single();
    }

    // ---- inscription ----

    @Test
    @DisplayName(
            "AUTH-10 : l'inscription répond 201 avec le compte en attente et ouvre une session restreinte (AUTH-16)")
    void auth10RegistrationCreatesAPendingAccountAndARestrictedSession() throws Exception {
        MvcResult result = register("valentin.it@exemple.ca", "valentin.it");

        JsonNode body = body(result);
        UUID id = UUID.fromString(body.get("id").asString());
        assertThat(id.version()).isEqualTo(7);
        assertThat(body.get("username").asString()).isEqualTo("valentin.it");
        assertThat(body.get("displayName").asString()).isEqualTo("Valentin");
        assertThat(body.get("email").asString()).isEqualTo("valentin.it@exemple.ca");
        assertThat(body.get("status").asString()).isEqualTo("PENDING_EMAIL");
        assertThat(body.get("factors").toString()).isEqualTo("[\"PASSWORD\"]");
        assertThat(body.get("verification").get("resendsRemaining").asLong()).isEqualTo(3);
        Instant expiresAt =
                Instant.parse(body.get("verification").get("codeExpiresAt").asString());
        assertThat(expiresAt)
                .isEqualTo(jdbc.sql("SELECT expires_at FROM email_codes WHERE user_id = ?")
                        .param(id)
                        .query(OffsetDateTime.class)
                        .single()
                        .toInstant());
        Cookie session = sessionOf(result);
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue();
    }

    @Test
    @DisplayName(
            "AUTH-14 : le courriel porte un code de 6 chiffres, dont seul le SHA-256 est en base, et l'identifiant")
    void auth14CodeMailMatchesTheStoredHash() throws Exception {
        UUID id = UUID.fromString(
                body(register("code.it@exemple.ca", "code.it")).get("id").asString());

        String code = codeSentTo("code.it@exemple.ca");
        SimpleMailMessage message = mail.sentTo("code.it@exemple.ca").getLast();
        assertThat(message.getSubject()).isEqualTo("Votre code de vérification SEAL");
        assertThat(message.getText())
                .isEqualTo(
                        "Votre code de vérification SEAL : " + code + ". Il expire dans 10 minutes. Votre identifiant"
                                + " : code.it. Si vous n'avez pas créé de compte SEAL, ignorez ce message.");
        byte[] stored = jdbc.sql("SELECT code_hash FROM email_codes WHERE user_id = ?")
                .param(id)
                .query(byte[].class)
                .single();
        assertThat(stored).isEqualTo(Digests.sha256(code.getBytes(StandardCharsets.US_ASCII)));
        assertThat(count(
                        "SELECT count(*) FROM user_credentials WHERE user_id = ? AND password_hash LIKE '{argon2}%'",
                        id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-20 : l'inscription est journalisée, courriel masqué (SEC-21)")
    void sec20RegistrationIsJournaled() throws Exception {
        UUID id = UUID.fromString(
                body(register("journal.it@exemple.ca", "journal.it")).get("id").asString());

        String details = jdbc.sql(
                        "SELECT details::text FROM security_events WHERE user_id = ? AND type = 'ACCOUNT_REGISTERED'")
                .param(id)
                .query(String.class)
                .single();
        assertThat(details).contains("j***@exemple.ca").doesNotContain("journal.it@");
    }

    @Test
    @DisplayName("AUTH-50 : la session ouverte est relue depuis Spring Session JDBC par GET /me")
    void auth50SessionIsReloadedFromTheDatabase() throws Exception {
        MvcResult result = register("relue.it@exemple.ca", "relue.it");
        String id = body(result).get("id").asString();

        mvc.perform(from(get("/api/v1/auth/me"), ip).cookie(sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andExpect(jsonPath("$.factors", contains("PASSWORD")));
        assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AUTH-60 : l'inscription retire le jeton CSRF ; l'ancien est refusé")
    void auth60OldCsrfTokenIsRefusedAfterRegistration() throws Exception {
        String oldToken = csrfToken();
        MvcResult result = mvc.perform(from(post("/api/v1/auth/register"), ip)
                        .cookie(new Cookie(CSRF_COOKIE, oldToken))
                        .header(CSRF_HEADER, oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("csrf.it@exemple.ca", "csrf.it"))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cleared = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();

        mvc.perform(from(post("/api/v1/auth/verify-email/resend"), ip)
                        .cookie(sessionOf(result))
                        .header(CSRF_HEADER, oldToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("AUTH-02 : un identifiant pris répond 409 USERNAME_TAKEN")
    void auth02TakenUsernameIsRefused() throws Exception {
        register("premier.it@exemple.ca", "pris.it");

        mvc.perform(registerRequest(registration("second.it@exemple.ca", "pris.it"), ip))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("AUTH-02 : un nom réservé répond 422 USERNAME_RESERVED")
    void auth02ReservedUsernameIsRefused() throws Exception {
        mvc.perform(registerRequest(registration("reserve.it@exemple.ca", "support"), ip))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USERNAME_RESERVED"));
    }

    @Test
    @DisplayName("AUTH-01 : un identifiant mal formé répond 400 USERNAME_INVALID, jamais VALIDATION_FAILED")
    void auth01MalformedUsernameIsRefused() throws Exception {
        mvc.perform(registerRequest(registration("invalide.it@exemple.ca", "Pas.Valide"), ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_INVALID"));
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe faible répond 422 PASSWORD_TOO_WEAK, sans rien créer ni envoyer")
    void auth11WeakPasswordCreatesNothing() throws Exception {
        Map<String, Object> body = registration("faible.it@exemple.ca", "faible.it");
        body.put("password", "faible.it2026!x");

        mvc.perform(registerRequest(body, ip))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
        assertThat(count("SELECT count(*) FROM users WHERE username = ?", "faible.it"))
                .isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-11 : une demi-paire isolée dans le mot de passe répond 422 PASSWORD_TOO_WEAK, jamais 500")
    void auth11LoneSurrogateIsTooWeak() throws Exception {
        Map<String, Object> body = registration("demipaire.it@exemple.ca", "demipaire.it");
        // L'échappement JSON de U+D800 donne une demi-paire isolée, que le hachage ne saurait pas encoder.
        String raw = json(body).replace(AuthClient.PASSWORD, "\\" + "ud800" + AuthClient.PASSWORD);

        mvc.perform(registerRequest(body, ip).content(raw))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
        assertThat(count("SELECT count(*) FROM users WHERE username = ?", "demipaire.it"))
                .isZero();
    }

    @Test
    @DisplayName("SEC-01 : un corps de plus de 16 Kio répond 413 REQUEST_REJECTED, avec les en-têtes, sans rien créer")
    void sec01BodyOver16KibIsRejected() throws Exception {
        Map<String, Object> atLimit = registration("taille.it@exemple.ca", "taille.it");
        Map<String, Object> overLimit = registration("taille2.it@exemple.ca", "taille2.it");

        mvc.perform(registerRequest(atLimit, ip).content(paddedTo(json(atLimit), 16_384)))
                .andExpect(status().isCreated());
        mvc.perform(registerRequest(overLimit, otherIp()).content(paddedTo(json(overLimit), 16_385)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"))
                .andExpect(header().exists("Content-Security-Policy"));
        assertThat(count("SELECT count(*) FROM users WHERE username = ?", "taille2.it"))
                .isZero();
    }

    /** JSON complété d'espaces, valides après la valeur, jusqu'à {@code bytes} octets. */
    private static String paddedTo(String json, int bytes) {
        return json + " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName(
            "AUTH-14 : un serveur SMTP en panne ne fait pas échouer l'inscription ; le journal ne cite pas l'adresse")
    void auth14SmtpFailureDoesNotFailTheRegistration(CapturedOutput output) throws Exception {
        mail.failWith(new MailSendException("Invalid Addresses: panne.it@exemple.ca"));

        register("panne.it@exemple.ca", "panne.it");

        assertThat(output.getAll())
                .contains("Échec de l'envoi du courriel : MailSendException")
                .doesNotContain("panne.it@");
    }

    @Test
    @DisplayName("SEC-01 : courriel, nom affiché et longueurs sont validés ; VALIDATION_FAILED nomme les champs")
    void sec01InvalidFieldsAreNamed() throws Exception {
        Map<String, Object> body = registration("pas-un-courriel", "champs.it");
        body.put("displayName", "Bob‮evil");
        body.put("password", "a".repeat(513));

        mvc.perform(registerRequest(body, ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("displayName", "email", "password")));
    }

    @Test
    @DisplayName("SEC-01 : un champ inconnu est refusé (400)")
    void sec01UnknownFieldIsRefused() throws Exception {
        Map<String, Object> body = registration("inconnu.it@exemple.ca", "inconnu.it");
        body.put("status", "ACTIVE");

        mvc.perform(registerRequest(body, ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(count("SELECT count(*) FROM users WHERE username = ?", "inconnu.it"))
                .isZero();
    }

    @Test
    @DisplayName("AUTH-60 : l'inscription sans jeton CSRF répond 403")
    void auth60RegistrationWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(from(post("/api/v1/auth/register"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("sanscsrf.it@exemple.ca", "sanscsrf.it"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("AUTH-10 : une session, même restreinte, ne peut pas s'inscrire à nouveau (403, ADR 0019)")
    void adr0019RegistrationIsForAnonymousOnly() throws Exception {
        Cookie session = sessionOf(register("deja.it@exemple.ca", "deja.it"));

        mvc.perform(registerRequest(registration("encore.it@exemple.ca", "encore.it"), ip)
                        .cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SEC-10 : la sixième inscription de l'heure depuis une IP répond 429, journalisée une fois")
    void sec10SixthRegistrationOfTheHourIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(registerRequest(registration("limite.it@exemple.ca", "admin"), ip))
                    .andExpect(status().isUnprocessableContent());
        }

        for (int i = 0; i < 2; i++) {
            mvc.perform(registerRequest(registration("limite.it@exemple.ca", "limite.it"), ip))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }
        assertThat(count(
                        "SELECT count(*) FROM security_events WHERE type = 'RATE_LIMIT_EXCEEDED' AND ip = CAST(? AS inet)",
                        ip))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM users WHERE username = ?", "limite.it"))
                .isZero();
    }

    @Test
    @DisplayName("AUTH-15 : même réponse, mêmes lignes et même nombre de courriels, que l'adresse soit libre ou non")
    void auth15ResponseDoesNotRevealAnExistingAddress() throws Exception {
        jdbc.sql("INSERT INTO users (username, display_name, email, status) VALUES "
                        + "('titulaire.it', 'Titulaire', 'Titulaire.IT@exemple.ca', 'ACTIVE')")
                .update();

        MvcResult taken = register("titulaire.it@EXEMPLE.ca", "squat.it");
        MvcResult free = mvc.perform(registerRequest(registration("libre.it@exemple.ca", "libre.it"), otherIp()))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(fieldNames(body(taken))).isEqualTo(fieldNames(body(free)));
        assertThat(body(taken).get("verification").get("resendsRemaining").asLong())
                .isEqualTo(3);
        assertThat(body(taken).get("verification").get("codeExpiresAt").isNull())
                .isFalse();
        assertThat(headerNames(taken.getResponse())).isEqualTo(headerNames(free.getResponse()));
        for (MvcResult result : List.of(taken, free)) {
            UUID id = UUID.fromString(body(result).get("id").asString());
            assertThat(count("SELECT count(*) FROM email_codes WHERE user_id = ?", id))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM user_credentials WHERE user_id = ?", id))
                    .isEqualTo(1);
        }
        assertThat(mail.sent()).hasSize(2);
        assertThat(mail.sentTo("titulaire.it@EXEMPLE.ca")).singleElement().satisfies(warning -> {
            assertThat(warning.getSubject()).isEqualTo("Tentative d'inscription sur SEAL");
            assertThat(warning.getText()).doesNotContain("squat.it").doesNotMatch(".*[0-9]{6}.*");
        });
        codeSentTo("libre.it@exemple.ca");
    }

    @Test
    @DisplayName(
            "AUTH-02 : deux inscriptions simultanées du même identifiant donnent un 201 et un 409, jamais un 500 (A06)")
    void a06ConcurrentRegistrationsOfTheSameUsername() throws Exception {
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            requests.add(registerRequest(registration("course" + i + ".it@exemple.ca", "course.it"), otherIp()));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(request).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- compte courant ----

    @Test
    @DisplayName("QA-05 : GET /me sans session répond 401 UNAUTHENTICATED")
    void qa05MeWithoutSessionIsUnauthenticated() throws Exception {
        mvc.perform(from(get("/api/v1/auth/me"), ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- disponibilité ----

    @Test
    @DisplayName("AUTH-05 : un identifiant libre est disponible, avec trois suggestions")
    void auth05FreeUsernameIsAvailable() throws Exception {
        mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "libre.dispo"), ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.suggestions", contains("libre.dispo2", "libre.dispo3", "libre.dispo4")));
    }

    @Test
    @DisplayName("AUTH-05 : un identifiant pris par un compte en attente n'est plus disponible")
    void auth05PendingAccountTakesItsUsername() throws Exception {
        register("pris.dispo@exemple.ca", "pris.dispo");

        mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "pris.dispo"), ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.suggestions", hasSize(3)))
                .andExpect(jsonPath("$.suggestions[0]").value("pris.dispo2"));
    }

    @Test
    @DisplayName("AUTH-16 : la session restreinte ne peut pas vérifier la disponibilité (403)")
    void auth16RestrictedSessionCannotCheckAvailability() throws Exception {
        Cookie session = sessionOf(register("restreint.dispo@exemple.ca", "restreint.dispo"));

        mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "autre"), ip)
                        .cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SEC-01 : u absent ou de plus de 64 caractères répond 400 VALIDATION_FAILED")
    void sec01AvailabilityParameterIsValidated() throws Exception {
        mvc.perform(from(get("/api/v1/auth/username-availability"), ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "a".repeat(65)), ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("u")));
    }

    @Test
    @DisplayName("SEC-10 : le 31e appel de la minute depuis une IP répond 429")
    void sec10ThirtyFirstAvailabilityCheckIsRateLimited() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "limite" + i), ip))
                    .andExpect(status().isOk());
        }

        mvc.perform(from(get("/api/v1/auth/username-availability").param("u", "limite"), ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[0-9]+")));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static Set<String> headerNames(MockHttpServletResponse response) {
        Set<String> names = new TreeSet<>(response.getHeaderNames());
        names.remove("Set-Cookie");
        return names;
    }
}
