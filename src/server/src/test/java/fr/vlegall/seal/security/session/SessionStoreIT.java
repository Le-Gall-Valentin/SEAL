package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.web.servlet.MockMvc;

@SealIntegrationTest
class SessionStoreIT {

    private static final String SESSION_COOKIE = "__Host-seal_sid";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    @Autowired
    private CookieSerializer cookieSerializer;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String savedSessionId() {
        return createAndSave(sessions);
    }

    private static <S extends Session> String createAndSave(SessionRepository<S> repository) {
        S session = repository.createSession();
        repository.save(session);
        return session.getId();
    }

    private long rowsFor(String sessionId) {
        return jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = ?")
                .param(sessionId)
                .query(Long.class)
                .single();
    }

    private long allRows() {
        return jdbc.sql("SELECT count(*) FROM spring_session").query(Long.class).single();
    }

    private void requestWithSession(String sessionId) throws Exception {
        String cookieValue = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/v1/unknown").secure(true).cookie(new Cookie(SESSION_COOKIE, cookieValue)));
    }

    @Test
    @DisplayName(
            "AUTH-51 : le cookie de session est __Host-seal_sid, Secure, HttpOnly, SameSite=Lax, Path=/, sans Domain")
    void auth51SessionCookieHasTheRequiredAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "id"));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith(SESSION_COOKIE + "=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContainIgnoringCase("Domain");
    }

    @Test
    @DisplayName("AUTH-50 : les sessions sont enregistrées en base par Spring Session JDBC")
    void auth50SessionsAreStoredInTheDatabase() {
        assertThat(rowsFor(savedSessionId())).isOne();
    }

    @Test
    @DisplayName("AUTH-52 : une session expire après 30 minutes d'inactivité")
    void auth52IdleTimeoutIsThirtyMinutes() {
        assertThat(sessions.createSession().getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("AUTH-52 : une session est supprimée 12 heures après sa création")
    void auth52SessionIsInvalidatedAfterTwelveHours() throws Exception {
        String sessionId = savedSessionId();
        clock.advance(Duration.ofHours(12));

        requestWithSession(sessionId);

        assertThat(rowsFor(sessionId)).isZero();
    }

    @Test
    @DisplayName("AUTH-52 : une session de moins de 12 heures est conservée")
    void auth52SessionYoungerThanTwelveHoursIsKept() throws Exception {
        String sessionId = savedSessionId();
        clock.advance(Duration.ofHours(11).plusMinutes(59));

        requestWithSession(sessionId);

        assertThat(rowsFor(sessionId)).isOne();
    }

    @Test
    @DisplayName("QA-05 : une requête anonyme refusée n'ajoute aucune session en base")
    void qa05AnonymousRequestCreatesNoSessionRow() throws Exception {
        long before = allRows();

        mvc.perform(get("/api/v1/unknown").secure(true));

        assertThat(allRows()).isEqualTo(before);
    }
}
