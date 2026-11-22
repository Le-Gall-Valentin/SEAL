package fr.vlegall.seal.authentication.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** ACC-01, ADR 0034 : {@code POST /api/v1/account/password}, de bout en bout dans le contexte complet. */
@SealIntegrationTest
class PasswordChangeIT {

    private static final String CHANGE = "/api/v1/account/password";
    private static final String NEW_PASSWORD = "raquette-sous-la-neige";
    private static final Pattern LINK =
            Pattern.compile("(?m)^https://\\S+/mot-de-passe/nouveau#jeton=([A-Za-z0-9_-]{43})$");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    private AuthClient client;

    /** Suffixe propre à ce test : la base est partagée par toute la série des tests d'intégration. */
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    private String name(String base) {
        return base + run;
    }

    private String email(String base) {
        return base + run + "@exemple.ca";
    }

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
    }

    private static Map<String, Object> body(String current, String next, @Nullable Boolean signOut) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", current);
        body.put("newPassword", next);
        if (signOut != null) {
            body.put("signOutOtherSessions", signOut);
        }
        return body;
    }

    /** Session ouverte par la connexion, depuis un autre appareil (une autre IP). */
    private Cookie otherDevice(String username, String password) throws Exception {
        MvcResult result = mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/login"), null, Map.of("login", username, "password", password)))
                .andExpect(status().isNoContent())
                .andReturn();
        return sessionOf(result);
    }

    private int loginStatus(String username, String password) throws Exception {
        return mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/login"), null, Map.of("login", username, "password", password)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int meStatus(Cookie session) throws Exception {
        return mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<String> journal(UUID user) {
        return jdbc.sql("SELECT type FROM security_events WHERE user_id = :user ORDER BY id")
                .param("user", user)
                .query(String.class)
                .list();
    }

    @Test
    @DisplayName(
            "ACC-01 : option par défaut, le mot de passe change, les autres sessions tombent, la courante est gardée")
    void acc01ChangesThePasswordAndSignsOutTheOthers() throws Exception {
        Registered account = client.register(email("change"), name("change"));
        Cookie current = client.verify(account);
        Cookie other = otherDevice(name("change"), PASSWORD);
        mail.reset();

        MvcResult result = mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, NEW_PASSWORD, null)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie renewed = sessionOf(result);
        assertThat(renewed.getValue()).isNotEqualTo(current.getValue());
        assertThat(meStatus(renewed)).isEqualTo(200);
        assertThat(meStatus(current))
                .as("ancien identifiant de la session courante")
                .isEqualTo(401);
        assertThat(meStatus(other)).as("autre session").isEqualTo(401);
        assertThat(loginStatus(name("change"), PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(name("change"), NEW_PASSWORD)).isEqualTo(204);
        assertThat(journal(account.id())).contains("PASSWORD_CHANGED", "SESSION_REVOKED");

        List<SimpleMailMessage> sent = mail.sentTo(email("change"));
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getSubject()).isEqualTo("Votre mot de passe SEAL a été changé");
        assertThat(sent.getFirst().getText())
                .isEqualTo("Le mot de passe de votre compte SEAL (identifiant : " + name("change")
                        + ") vient d'être changé."
                        + " Si ce n'est pas vous, utilisez « Mot de passe oublié » sur la page de connexion.");
    }

    @Test
    @DisplayName("ACC-01, ADR 0034 (point 1) : option décochée, l'autre session reste ouverte")
    void acc01UncheckedOptionKeepsTheOtherSessions() throws Exception {
        Registered account = client.register(email("garde"), name("garde"));
        Cookie current = client.verify(account);
        Cookie other = otherDevice(name("garde"), PASSWORD);

        MvcResult result = mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, NEW_PASSWORD, false)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(meStatus(sessionOf(result))).isEqualTo(200);
        assertThat(meStatus(other)).as("autre session").isEqualTo(200);
        assertThat(loginStatus(name("garde"), NEW_PASSWORD)).isEqualTo(204);
        assertThat(journal(account.id())).contains("PASSWORD_CHANGED").doesNotContain("SESSION_REVOKED");
    }

    @Test
    @DisplayName(
            "ACC-01 : mot de passe actuel faux, 401 INVALID_CREDENTIALS ; rien ne change et la session reste ouverte")
    void acc01WrongCurrentPasswordChangesNothing() throws Exception {
        Registered account = client.register(email("faux"), name("faux"));
        Cookie current = client.verify(account);

        mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD + "!", NEW_PASSWORD, true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(meStatus(current)).isEqualTo(200);
        assertThat(loginStatus(name("faux"), PASSWORD)).isEqualTo(204);
        assertThat(journal(account.id())).contains("LOGIN_FAILED").doesNotContain("PASSWORD_CHANGED");
    }

    @Test
    @DisplayName("ACC-01, AUTH-11 : nouveau mot de passe hors de la politique, 422 PASSWORD_TOO_WEAK ; rien ne change")
    void acc01WeakNewPasswordIsRefused() throws Exception {
        Cookie current = client.verify(client.register(email("faible"), name("faible")));

        mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, "faible", true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));

        assertThat(loginStatus(name("faible"), PASSWORD)).isEqualTo(204);
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0034 : un nouveau mot de passe trop faible, avec le bon mot de passe actuel, ne compte pas comme un échec")
    void sec10WeakNewPasswordIsNotAFailure() throws Exception {
        Cookie current = client.verify(client.register(email("faibles"), name("faibles")));

        for (int i = 0; i < 6; i++) {
            mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, "faible", true)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
        }
        mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, NEW_PASSWORD, false)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ACC-01, ADR 0034 : un lien de réinitialisation envoyé avant le changement ne sert plus")
    void acc01ChangeKillsLiveResetLinks() throws Exception {
        Cookie current = client.verify(client.register(email("lien"), name("lien")));
        mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", email("lien"))))
                .andExpect(status().isNoContent());
        List<SimpleMailMessage> sent = mail.sentTo(email("lien"));
        Matcher link = LINK.matcher(sent.getLast().getText());
        assertThat(link.find()).as("lien dans le courriel").isTrue();

        mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, NEW_PASSWORD, false)))
                .andExpect(status().isNoContent());

        mvc.perform(client.unsafe(post("/api/v1/auth/password-reset/inspect"), null, Map.of("token", link.group(1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0034 (point 4) : les échecs de ACC-01 et de la ré-authentification partagent 5 essais par 15 minutes")
    void sec10FailuresAreSharedWithReauthentication() throws Exception {
        Cookie current = client.verify(client.register(email("partage"), name("partage")));

        for (int i = 0; i < 3; i++) {
            mvc.perform(client.unsafe(post("/api/v1/auth/reauth"), current, Map.of("password", PASSWORD + i)))
                    .andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD + i, NEW_PASSWORD, true)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(client.unsafe(post(CHANGE), current, body(PASSWORD, NEW_PASSWORD, true)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName(
            "QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte, 400 pour un champ inconnu ou trop long")
    void qa05AccessRulesAndValidation() throws Exception {
        Registered pending = client.register(email("regles"), name("regles"));

        mvc.perform(client.unsafe(post(CHANGE), null, body(PASSWORD, NEW_PASSWORD, true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(post(CHANGE), pending.session(), body(PASSWORD, NEW_PASSWORD, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Cookie full = client.verify(pending);
        mvc.perform(client.from(post(CHANGE))
                        .cookie(full)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        Map<String, Object> unknown = body(PASSWORD, NEW_PASSWORD, true);
        unknown.put("username", "autre");
        mvc.perform(client.unsafe(post(CHANGE), full, unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.unsafe(post(CHANGE), full, body(PASSWORD, "x".repeat(513), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("newPassword"));
        mvc.perform(client.unsafe(post(CHANGE), full, Map.of("newPassword", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(loginStatus(name("regles"), PASSWORD)).isEqualTo(204);
    }
}
