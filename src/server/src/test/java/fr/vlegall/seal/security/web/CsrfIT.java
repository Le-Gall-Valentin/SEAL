package fr.vlegall.seal.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SealIntegrationTest
class CsrfIT {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private String csrfToken() throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/csrf").secure(true))
                .andReturn()
                .getResponse()
                .getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    @Test
    @DisplayName("AUTH-60 : GET /api/v1/csrf dépose le cookie XSRF-TOKEN lisible par le client")
    void auth60CsrfEndpointSetsTheTokenCookie() throws Exception {
        mvc.perform(get("/api/v1/csrf").secure(true))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(CSRF_COOKIE))
                .andExpect(cookie().httpOnly(CSRF_COOKIE, false))
                .andExpect(cookie().secure(CSRF_COOKIE, true))
                .andExpect(cookie().path(CSRF_COOKIE, "/"));
    }

    @Test
    @DisplayName("QA-05 : GET /api/v1/csrf, première route de tout client anonyme, ne crée aucune session")
    void qa05CsrfEndpointCreatesNoSession() throws Exception {
        long before = jdbc.sql("SELECT count(*) FROM spring_session")
                .query(Long.class)
                .single();

        MvcResult result = mvc.perform(get("/api/v1/csrf").secure(true)).andReturn();

        assertThat(jdbc.sql("SELECT count(*) FROM spring_session")
                        .query(Long.class)
                        .single())
                .isEqualTo(before);
        assertThat(result.getResponse().getCookie("__Host-seal_sid")).isNull();
    }

    @Test
    @DisplayName("AUTH-60 : une requête non sûre sans jeton CSRF répond 403 FORBIDDEN")
    void auth60UnsafeRequestWithoutTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/unknown").secure(true))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("AUTH-60 : avec le cookie et l'en-tête X-XSRF-TOKEN, la requête passe le filtre CSRF")
    void auth60UnsafeRequestWithTokenPassesTheCsrfFilter() throws Exception {
        String token = csrfToken();
        mvc.perform(post("/api/v1/unknown")
                        .secure(true)
                        .cookie(new Cookie(CSRF_COOKIE, token))
                        .header(CSRF_HEADER, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Attaque CSRF (16.1) : un formulaire envoyé depuis une autre origine est refusé")
    void csrfAttackFromAnotherOriginIsRejected() throws Exception {
        String token = csrfToken();
        mvc.perform(post("/api/v1/unknown")
                        .secure(true)
                        .header("Origin", "https://evil.example")
                        .cookie(new Cookie(CSRF_COOKIE, token))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("displayName=pirate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("QA-05 : une requête anonyme répond 401 UNAUTHENTICATED sans créer de session")
    void qa05AnonymousRequestIsUnauthenticatedWithoutSession() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/unknown").secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
        assertThat(result.getResponse().getCookie("__Host-seal_sid")).isNull();
    }

    @Test
    @DisplayName("AUTH-60 : une requête non sûre envoyée directement sur /error est refusée par le filtre CSRF")
    void auth60ErrorEndpointIsCsrfProtected() throws Exception {
        mvc.perform(post("/error").secure(true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A01 : le point de déconnexion par défaut de Spring Security n'existe pas")
    void a01DefaultLogoutEndpointDoesNotExist() throws Exception {
        String token = csrfToken();
        mvc.perform(post("/logout")
                        .secure(true)
                        .cookie(new Cookie(CSRF_COOKIE, token))
                        .header(CSRF_HEADER, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
