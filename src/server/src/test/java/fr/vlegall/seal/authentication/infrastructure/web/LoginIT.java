package fr.vlegall.seal.authentication.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static fr.vlegall.seal.support.AuthClient.SESSION_COOKIE;
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
import fr.vlegall.seal.support.PasswordHashingProbe;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Connexion et déconnexion, de bout en bout dans le contexte complet (AUTH-20 à AUTH-23, AUTH-53). */
@SealIntegrationTest
class LoginIT {

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

    private MockHttpServletRequestBuilder loginRequest(AuthClient from, String login, String password)
            throws Exception {
        return from.unsafe(post("/api/v1/auth/login"), null, Map.of("login", login, "password", password));
    }

    private MvcResult login(AuthClient from, String login, String password) throws Exception {
        return mvc.perform(loginRequest(from, login, password)).andReturn();
    }

    /** Compte actif : inscrit, puis vérifié. */
    private Registered active(String username) throws Exception {
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register(username + "@exemple.ca", username);
        owner.verify(account);
        return account;
    }

    private Registered pending(String username) throws Exception {
        return new AuthClient(mvc, mail).register(username + "@exemple.ca", username);
    }

    @Test
    @DisplayName("AUTH-20 : un compte actif obtient une session complète, sous un nouvel identifiant (AUTH-23)")
    void auth20ActiveAccountGetsAFullSession() throws Exception {
        Registered account = active(unique("actif"));
        Cookie planted = new Cookie(SESSION_COOKIE, "session-plantee");

        MvcResult result = mvc.perform(
                        loginRequest(client, account.username(), PASSWORD).cookie(planted))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie session = sessionOf(result);
        assertThat(session.getValue()).isNotEqualTo(planted.getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(client.from(get("/api/v1/auth/username-availability").param("u", "libre"))
                        .cookie(session))
                .andExpect(status().isOk());
        // AUTH-60 : la connexion retire le jeton CSRF d'avant ; le client en recharge un.
        assertThat(result.getResponse().getCookie(AuthClient.CSRF_COOKIE))
                .isNotNull()
                .extracting(Cookie::getMaxAge)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("AUTH-16 : un compte en attente, par son identifiant, obtient une session restreinte")
    void auth16PendingAccountGetsARestrictedSession() throws Exception {
        Registered account = pending(unique("attente"));

        MvcResult result = mvc.perform(loginRequest(client, account.username(), PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie session = sessionOf(result);
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"));
        mvc.perform(client.from(get("/api/v1/auth/username-availability").param("u", "libre"))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUTH-20 : un compte en attente ne se connecte pas par son courriel (ADR 0019)")
    void auth20PendingAccountByEmailIsRejected() throws Exception {
        Registered account = pending(unique("att.mail"));

        mvc.perform(loginRequest(client, account.email(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AUTH-20 : la saisie est normalisée : majuscules d'identifiant ou de courriel acceptées (ADR 0021)")
    void auth20FoldedLoginFindsTheAccount() throws Exception {
        Registered account = active(unique("casse"));

        mvc.perform(loginRequest(client, account.username().toUpperCase(java.util.Locale.ROOT), PASSWORD))
                .andExpect(status().isNoContent());
        mvc.perform(loginRequest(
                        new AuthClient(mvc, mail), account.email().toUpperCase(java.util.Locale.ROOT), PASSWORD))
                .andExpect(status().isNoContent());
    }

    private static Map<String, Object> shape(MvcResult result) throws Exception {
        MockHttpServletResponse response = result.getResponse();
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("status", response.getStatus());
        shape.put("body", response.getContentAsString());
        List<String> headers = new ArrayList<>(response.getHeaderNames());
        Collections.sort(headers);
        shape.put("headers", headers);
        return shape;
    }

    @Test
    @DisplayName("AUTH-21 : inconnu, mauvais mot de passe, supprimé ou courriel en attente : même réponse, même durée")
    void auth21FailuresAreIndistinguishable() throws Exception {
        Registered known = active(unique("connu"));
        Registered deleted = active(unique("supprime"));
        jdbc.sql("UPDATE users SET status = 'DELETED', deleted_at = now() WHERE id = ?")
                .param(deleted.id())
                .update();
        Registered waiting = pending(unique("enattente"));
        List<String[]> cases = List.of(
                new String[] {"inconnu." + UUID.randomUUID().toString().substring(0, 6), PASSWORD},
                new String[] {known.username(), "mauvais-mot-de-passe-long"},
                new String[] {deleted.username(), PASSWORD},
                new String[] {waiting.email(), PASSWORD});
        List<Map<String, Object>> shapes = new ArrayList<>();
        List<List<Long>> durations = new ArrayList<>();
        cases.forEach(c -> durations.add(new ArrayList<>()));
        for (int round = 0; round < 10; round++) {
            for (int i = 0; i < cases.size(); i++) {
                MockHttpServletRequestBuilder request =
                        loginRequest(new AuthClient(mvc, mail), cases.get(i)[0], cases.get(i)[1]);
                long start = System.nanoTime();
                MvcResult result = mvc.perform(request).andReturn();
                long elapsed = System.nanoTime() - start;
                if (round > 0) {
                    durations.get(i).add(elapsed);
                }
                if (round == 0) {
                    shapes.add(shape(result));
                }
            }
            // Les seaux par identifiant se remplissent : chaque tour reste sous les 5 échecs (AUTH-22).
            clock.advance(Duration.ofMinutes(16));
        }

        assertThat(shapes).allSatisfy(s -> assertThat(s).isEqualTo(shapes.getFirst()));
        assertThat(shapes.getFirst()).containsEntry("status", 401);
        assertThat((String) shapes.getFirst().get("body")).contains("\"code\":\"INVALID_CREDENTIALS\"");
        List<Long> medians = durations.stream().map(LoginIT::median).toList();
        long slowest = Collections.max(medians);
        long fastest = Collections.min(medians);
        assertThat((double) slowest / fastest).as("médianes %s", medians).isLessThan(1.25);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    @Test
    @DisplayName("AUTH-22 : le 6e échec en 15 minutes répond 429 avec Retry-After, même pour un identifiant inconnu")
    void auth22SixthFailureIsRateLimited() throws Exception {
        for (String login : List.of(active(unique("limite")).username(), unique("fantome"))) {
            for (int i = 0; i < 5; i++) {
                mvc.perform(loginRequest(new AuthClient(mvc, mail), login, "mauvais-mot-de-passe-long"))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(loginRequest(new AuthClient(mvc, mail), login, PASSWORD))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }
    }

    @Test
    @DisplayName("AUTH-22 : une connexion réussie rend son jeton ; seuls les échecs comptent")
    void auth22SuccessRefundsItsToken() throws Exception {
        Registered account = active(unique("rendu"));
        for (int i = 0; i < 4; i++) {
            mvc.perform(loginRequest(client, account.username(), "mauvais-mot-de-passe-long"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(loginRequest(new AuthClient(mvc, mail), account.username(), PASSWORD))
                .andExpect(status().isNoContent());

        mvc.perform(loginRequest(client, account.username(), "mauvais-mot-de-passe-long"))
                .andExpect(status().isUnauthorized());
        mvc.perform(loginRequest(client, account.username(), "mauvais-mot-de-passe-long"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("AUTH-22 : les variantes de casse d'une saisie partagent les mêmes 5 échecs (ADR 0021)")
    void auth22FoldedLoginSharesTheBucket() throws Exception {
        String login = unique("variantes");
        for (String variant : List.of(
                login,
                login.toUpperCase(java.util.Locale.ROOT),
                "V" + login.substring(1),
                login,
                login.toUpperCase(java.util.Locale.ROOT))) {
            mvc.perform(loginRequest(new AuthClient(mvc, mail), variant, "mauvais-mot-de-passe-long"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(loginRequest(new AuthClient(mvc, mail), login, "mauvais-mot-de-passe-long"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("AUTH-22 : au-delà de 20 échecs depuis une même IP, toute connexion répond 429")
    void auth22IpLimitAfterTwentyFailures() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(loginRequest(client, unique("ip"), "mauvais-mot-de-passe-long"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(loginRequest(client, unique("ip"), "mauvais-mot-de-passe-long"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("SEC-10 : 20 adresses IPv6 d'un même /64 partagent les 20 échecs par IP (ADR 0021)")
    void sec10Ipv6AddressesOfOneSlash64ShareTheIpLimit() throws Exception {
        String prefix = "2001:db8:7:7::";
        for (int i = 1; i <= 20; i++) {
            String address = prefix + Integer.toHexString(i);
            mvc.perform(loginRequest(client, unique("v6"), "mauvais-mot-de-passe-long")
                            .with(r -> {
                                r.setRemoteAddr(address);
                                return r;
                            }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(loginRequest(client, unique("v6"), "mauvais-mot-de-passe-long")
                        .with(r -> {
                            r.setRemoteAddr(prefix + "beef");
                            return r;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("AUTH-22 : six échecs simultanés sur un identifiant font au plus 5 vérifications Argon2id")
    void auth22ConcurrentFailuresVerifyAtMostFive() throws Exception {
        String login = active(unique("course")).username();
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(loginRequest(new AuthClient(mvc, mail), login, "mauvais-mot-de-passe-long"));
        }
        hashing.reset();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(request).andReturn();
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsOnly(401, 429).contains(429);
        assertThat(hashing.observations()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("QA-05 : la connexion exige un jeton CSRF et refuse toute session, restreinte ou complète (403)")
    void qa05LoginRequiresCsrfAndAnonymity() throws Exception {
        Registered account = pending(unique("csrf"));
        Cookie restricted = account.session();

        mvc.perform(client.from(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.unsafe(
                        post("/api/v1/auth/login"),
                        restricted,
                        Map.of("login", account.username(), "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SEC-01 : champ inconnu, champ manquant ou trop long : 400 VALIDATION_FAILED, sans vérification")
    void sec01InvalidBodiesAreRejected() throws Exception {
        hashing.reset();
        List<Map<String, ?>> bodies = List.of(
                Map.of("login", "valentin", "password", PASSWORD, "remember", true),
                Map.of("login", "valentin"),
                Map.of("login", "v".repeat(255), "password", PASSWORD),
                Map.of("login", "valentin", "password", "p".repeat(513)));
        for (Map<String, ?> body : bodies) {
            mvc.perform(client.unsafe(post("/api/v1/auth/login"), null, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(hashing.observations()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe à demi-paire isolée répond 401, jamais 500")
    void auth11LoneSurrogatePasswordIsRejected() throws Exception {
        Registered account = active(unique("demipaire"));
        String raw = "{\"login\":\"" + account.username() + "\",\"password\":\"" + "\\" + "ud800" + PASSWORD + "\"}";

        mvc.perform(client.unsafe(post("/api/v1/auth/login"), null, null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(raw))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AUTH-53 : la déconnexion ferme la session et expire ses cookies, aussi pour une session restreinte")
    void auth53LogoutClosesTheSession() throws Exception {
        Registered account = active(unique("sortie"));
        Cookie full = sessionOf(login(client, account.username(), PASSWORD));
        Cookie restricted = pending(unique("sortie.att")).session();

        for (Cookie session : List.of(full, restricted)) {
            MvcResult result = mvc.perform(client.unsafe(post("/api/v1/auth/logout"), session, null))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertThat(result.getResponse().getCookie(SESSION_COOKIE))
                    .isNotNull()
                    .extracting(Cookie::getMaxAge)
                    .isEqualTo(0);
            assertThat(result.getResponse().getCookie(AuthClient.CSRF_COOKIE))
                    .isNotNull()
                    .extracting(Cookie::getMaxAge)
                    .isEqualTo(0);
            mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session)).andExpect(status().isUnauthorized());
            // Une seconde déconnexion trouve la session déjà fermée.
            mvc.perform(client.unsafe(post("/api/v1/auth/logout"), session, null))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    @DisplayName("QA-05 : la déconnexion exige une session (401) et un jeton CSRF (403)")
    void qa05LogoutRequiresASessionAndACsrfToken() throws Exception {
        Registered account = active(unique("sortie.csrf"));
        Cookie session = sessionOf(login(client, account.username(), PASSWORD));

        mvc.perform(client.unsafe(post("/api/v1/auth/logout"), null, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.from(post("/api/v1/auth/logout")).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SEC-21 : un mot de passe tapé dans le champ identifiant n'est jamais journalisé, même en 429")
    void sec21PasswordTypedAsLoginIsNeverJournaled() throws Exception {
        String typed = "MonMotDePasse" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 6; i++) {
            login(new AuthClient(mvc, mail), typed, "peu-importe-le-mot-de-passe");
        }

        assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE lower(details::text) LIKE ?")
                        .param("%" + typed.toLowerCase(java.util.Locale.ROOT) + "%")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("SEC-20 : connexions réussie et refusée, et déconnexion, sont journalisées sans mot de passe")
    void sec20LoginAndLogoutAreJournaled() throws Exception {
        Registered account = active(unique("journal"));
        UUID id = account.id();
        login(client, account.username(), "mauvais-mot-de-passe-long");
        Cookie session = sessionOf(login(client, account.username(), PASSWORD));
        mvc.perform(client.unsafe(post("/api/v1/auth/logout"), session, null));

        List<String> types = jdbc.sql("SELECT type FROM security_events WHERE user_id = ? AND type LIKE 'LOG%' "
                        + "ORDER BY created_at, id")
                .param(id)
                .query(String.class)
                .list();
        assertThat(types).containsExactly("LOGIN_FAILED", "LOGIN_SUCCEEDED", "LOGOUT");
        assertThat(jdbc.sql("SELECT details->>'login' FROM security_events WHERE user_id = ? AND type = 'LOGIN_FAILED'")
                        .param(id)
                        .query(String.class)
                        .single())
                .as("la saisie n'est pas recopiée ; le compte suffit (ADR 0013)")
                .isEqualTo("***");
        assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE details::text LIKE ?")
                        .param("%" + PASSWORD + "%")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
