package fr.vlegall.seal.security.session;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AUTH-23, AUTH-53, ADR 0045 : attaques de check-security contre la migration des sessions, rejouées en permanence.
 */
@SealIntegrationTest
class SessionMigrationAttackIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SessionRepositoryFilter<? extends Session> sessionFilter;

    @Autowired
    private SessionMigration migration;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    private List<SecurityFilterChain> chains;

    private AuthClient client;
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private static String sessionId(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    private long rowsOf(UUID user) {
        return jdbc.sql("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?")
                .param(user.toString())
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName(
            "AUTH-53, ADR 0045 : une session supprimée par une autre requête n'est pas ranimée par la migration (échec fermé)")
    void auth53MigrationOfADeletedSessionRevivesNothing() throws Exception {
        Registered victim = client.register("perime" + run + "@exemple.ca", "perime" + run);
        Cookie full = client.verify(victim);
        String id = sessionId(full);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/reauth");
        request.setCookies(full);

        Throwable thrown =
                catchThrowable(() -> sessionFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    HttpServletRequest http = (HttpServletRequest) req;
                    assertThat(http.getSession(false))
                            .as("session lue au début")
                            .isNotNull();
                    // Déconnexion ou révocation validée entre-temps par une autre requête.
                    sessions.deleteById(id);
                    migration.onAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated("x", null, List.of()),
                            http,
                            (HttpServletResponse) res);
                }));

        assertThat(thrown).isInstanceOf(SessionAuthenticationException.class);
        assertThat(rowsOf(victim.id()))
                .as("session supprimée puis ranimée par la migration")
                .isZero();
    }

    @Test
    @DisplayName(
            "AUTH-23, scénario 16.1, ADR 0045 : avec deux cookies de session, la migration n'adopte pas la session de l'autre")
    void auth23MigrationNeverAdoptsAnotherExistingSession() throws Exception {
        Cookie victim = client.verify(client.register("cible" + run + "@exemple.ca", "cible" + run));
        Cookie attacker = client.verify(client.register("intrus" + run + "@exemple.ca", "intrus" + run));

        MvcResult refused = mvc.perform(client.unsafe(post("/api/v1/auth/reauth"), victim, Map.of("password", PASSWORD))
                        .cookie(attacker))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();
        Cookie expired = refused.getResponse().getCookie(AuthClient.SESSION_COOKIE);
        assertThat(expired).as("cookie de session expiré").isNotNull();
        assertThat(expired.getMaxAge()).isZero();

        // La session de l'intrus, qui aurait reçu les droits de la victime, est fermée.
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(attacker)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
            "ADR 0032 point 7, ADR 0045 : aucun SessionManagementFilter implicite ne renomme ni n'écrit la session")
    void adr0045NoImplicitSessionManagementFilter() {
        assertThat(chains)
                .allSatisfy(
                        chain -> assertThat(chain.getFilters()).noneMatch(SessionManagementFilter.class::isInstance));
    }
}
