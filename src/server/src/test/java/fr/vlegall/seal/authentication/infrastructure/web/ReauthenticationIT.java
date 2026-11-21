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
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-55, ADR 0034 : {@code POST /api/v1/auth/reauth}, de bout en bout dans le contexte complet. */
@SealIntegrationTest
class ReauthenticationIT {

    private static final String REAUTH = "/api/v1/auth/reauth";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

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
        clock.reset();
    }

    private List<String> journal(UUID user) {
        return jdbc.sql("SELECT type FROM security_events WHERE user_id = :user ORDER BY id")
                .param("user", user)
                .query(String.class)
                .list();
    }

    @Test
    @DisplayName(
            "AUTH-55, AUTH-23 : le bon mot de passe répond 204 sous un nouvel identifiant ; l'ancien cookie ne sert plus")
    void auth55RightPasswordRenewsTheSession() throws Exception {
        Registered account = client.register(email("sudo"), name("sudo"));
        Cookie full = client.verify(account);

        MvcResult result = mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie renewed = sessionOf(result);
        assertThat(renewed.getValue()).isNotEqualTo(full.getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(full)).andExpect(status().isUnauthorized());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(renewed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factors[0]").value("PASSWORD"));
        assertThat(journal(account.id())).doesNotContain("LOGIN_FAILED");
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0034 : un mauvais mot de passe répond 401 INVALID_CREDENTIALS, garde la session et est journalisé")
    void auth55WrongPasswordKeepsTheSession() throws Exception {
        Registered account = client.register(email("erreur"), name("erreur"));
        Cookie full = client.verify(account);

        mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD + "!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(full)).andExpect(status().isOk());
        assertThat(journal(account.id())).containsSubsequence("LOGIN_FAILED");
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0034 : 5 échecs par 15 minutes pour le compte, puis 429 avec Retry-After, même avec le bon mot de passe")
    void sec10FiveFailuresPerAccountThenRateLimited() throws Exception {
        Cookie full = client.verify(client.register(email("limite"), name("limite")));

        for (int i = 0; i < 5; i++) {
            mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        clock.advance(Duration.ofMinutes(16));
        mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("SEC-10, ADR 0034 : une réussite rend les jetons d'échec ; seuls les échecs comptent")
    void sec10SuccessRefundsTheFailureTokens() throws Exception {
        Cookie session = client.verify(client.register(email("rendu"), name("rendu")));

        for (int i = 0; i < 8; i++) {
            MvcResult result = mvc.perform(client.unsafe(post(REAUTH), session, Map.of("password", PASSWORD)))
                    .andExpect(status().isNoContent())
                    .andReturn();
            session = sessionOf(result);
        }
    }

    @Test
    @DisplayName(
            "QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte, 400 pour un champ inconnu ou trop long")
    void qa05AccessRulesAndValidation() throws Exception {
        Registered pending = client.register(email("acces"), name("acces"));

        mvc.perform(client.unsafe(post(REAUTH), null, Map.of("password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(post(REAUTH), pending.session(), Map.of("password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Cookie full = client.verify(pending);
        mvc.perform(client.from(post(REAUTH))
                        .cookie(full)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", PASSWORD, "login", name("acces"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.unsafe(post(REAUTH), full, Map.of("password", "x".repeat(513))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("password"));
        mvc.perform(client.unsafe(post(REAUTH), full, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
