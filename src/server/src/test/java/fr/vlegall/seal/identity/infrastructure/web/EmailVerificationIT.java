package fr.vlegall.seal.identity.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.body;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Vérification, renvoi et changement du courriel, de bout en bout dans le contexte complet. */
@SealIntegrationTest
class EmailVerificationIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

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

    private MockHttpServletRequestBuilder verifyRequest(Cookie session, String code) throws Exception {
        return client.unsafe(post("/api/v1/auth/verify-email"), session, Map.of("code", code));
    }

    private String wrongCode(String right) {
        return right.equals("000000") ? "111111" : "000000";
    }

    @Test
    @DisplayName("AUTH-16 : le bon code active le compte et rend la session complète, sous un nouvel identifiant")
    void auth16RightCodeMakesTheSessionFull() throws Exception {
        Registered account = client.register("parcours@exemple.ca", "parcours");

        MvcResult result = mvc.perform(verifyRequest(account.session(), client.codeSentTo("parcours@exemple.ca")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.verification").value(nullValue()))
                .andReturn();

        Cookie full = sessionOf(result);
        assertThat(full.getValue()).isNotEqualTo(account.session().getValue());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(account.session()))
                .andExpect(status().isUnauthorized());
        mvc.perform(client.from(get("/api/v1/auth/username-availability").param("u", "libre"))
                        .cookie(full))
                .andExpect(status().isOk());
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("AUTH-14 : 5 mauvais codes répondent INVALID_CODE, puis même le bon répond CODE_EXPIRED")
    void auth14FiveWrongCodesExhaustTheCode() throws Exception {
        Registered account = client.register("essais@exemple.ca", "essais");
        String code = client.codeSentTo("essais@exemple.ca");

        for (int i = 0; i < 5; i++) {
            mvc.perform(verifyRequest(account.session(), wrongCode(code)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }

        mvc.perform(verifyRequest(account.session(), code))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("AUTH-14 : un code expire au bout de 10 minutes")
    void auth14CodeExpiresAfterTenMinutes() throws Exception {
        Registered account = client.register("expire@exemple.ca", "expire");
        String code = client.codeSentTo("expire@exemple.ca");
        clock.advance(Duration.ofMinutes(10));

        mvc.perform(verifyRequest(account.session(), code))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("AUTH-14 : un renvoi remplace le code ; l'ancien ne vaut plus")
    void auth14ResendReplacesTheCode() throws Exception {
        Registered account = client.register("renvoi@exemple.ca", "renvoi");
        String first = client.codeSentTo("renvoi@exemple.ca");

        mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/resend"), account.session(), null))
                .andExpect(status().isNoContent());
        String second = client.codeSentTo("renvoi@exemple.ca");

        if (!second.equals(first)) {
            mvc.perform(verifyRequest(account.session(), first))
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        mvc.perform(verifyRequest(account.session(), second)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-17 : le changement remplace l'adresse, est journalisé et envoie le code à la nouvelle adresse")
    void auth17ChangeReplacesTheAddress() throws Exception {
        Registered account = client.register("ancienne@exemple.ca", "changement");

        mvc.perform(client.unsafe(
                        post("/api/v1/auth/verify-email/change"),
                        account.session(),
                        Map.of("email", "nouvelle@exemple.ca")))
                .andExpect(status().isNoContent());

        String code = client.codeSentTo("nouvelle@exemple.ca");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(account.session()))
                .andExpect(jsonPath("$.email").value("nouvelle@exemple.ca"));
        assertThat(jdbc.sql("SELECT details::text FROM security_events WHERE user_id = ? AND type = 'EMAIL_CHANGED'")
                        .param(account.id())
                        .query(String.class)
                        .single())
                .contains("n***@exemple.ca")
                .doesNotContain("nouvelle@");
        mvc.perform(verifyRequest(account.session(), code)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-10 : renvoi et changement partagent 3 essais par heure ; le 4e répond 429")
    void sec10ResendAndChangeShareThreePerHour() throws Exception {
        Registered account = client.register("quota@exemple.ca", "quota");
        mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/resend"), account.session(), null))
                .andExpect(status().isNoContent());
        mvc.perform(client.unsafe(
                        post("/api/v1/auth/verify-email/change"),
                        account.session(),
                        Map.of("email", "quota2@exemple.ca")))
                .andExpect(status().isNoContent());
        mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/resend"), account.session(), null))
                .andExpect(status().isNoContent());

        mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/resend"), account.session(), null))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(account.session()))
                .andExpect(jsonPath("$.verification.resendsRemaining").value(0));
    }

    @Test
    @DisplayName("SEC-01 : un code qui n'a pas 6 chiffres répond 400 VALIDATION_FAILED, sans compter d'essai")
    void sec01MalformedCodeIsAValidationError() throws Exception {
        Registered account = client.register("format@exemple.ca", "format");

        mvc.perform(verifyRequest(account.session(), "12345a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("code"));
        assertThat(jdbc.sql("SELECT attempts FROM email_codes WHERE user_id = ?")
                        .param(account.id())
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("QA-05 : sans session, la vérification, le renvoi et le changement répondent 401")
    void qa05RoutesRequireASession() throws Exception {
        for (String path : List.of(
                "/api/v1/auth/verify-email", "/api/v1/auth/verify-email/resend", "/api/v1/auth/verify-email/change")) {
            mvc.perform(client.unsafe(post(path), null, Map.of()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    @DisplayName("QA-05 : sans jeton CSRF, la vérification, le renvoi et le changement répondent 403")
    void qa05RoutesRequireACsrfToken() throws Exception {
        Registered account = client.register("sanscsrf2@exemple.ca", "sanscsrf2");
        for (String path : List.of(
                "/api/v1/auth/verify-email", "/api/v1/auth/verify-email/resend", "/api/v1/auth/verify-email/change")) {
            mvc.perform(client.from(post(path))
                            .cookie(account.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("AUTH-16 : une session complète ne peut plus vérifier, renvoyer ni changer (403)")
    void auth16FullSessionCannotUseTheVerificationRoutes() throws Exception {
        Cookie full = client.verify(client.register("complete@exemple.ca", "complete"));
        for (String path : List.of(
                "/api/v1/auth/verify-email", "/api/v1/auth/verify-email/resend", "/api/v1/auth/verify-email/change")) {
            mvc.perform(client.unsafe(post(path), full, Map.of("code", "123456", "email", "x@exemple.ca")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    /** Lance les requêtes en même temps et rend leurs résultats, dans l'ordre des requêtes. */
    private List<MvcResult> race(List<MockHttpServletRequestBuilder> requests) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(request).andReturn();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertOneSuccessAndOneCodeExpired(List<MvcResult> results) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        for (MvcResult result : results) {
            statuses.add(result.getResponse().getStatus());
            bodies.add(result.getResponse().getContentAsString());
        }
        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat(bodies).anyMatch(text -> text.contains("CODE_EXPIRED"));
    }

    private String codeExpiresAt(Cookie session) throws Exception {
        return body(mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                        .andReturn())
                .get("verification")
                .get("codeExpiresAt")
                .toString();
    }

    @Test
    @DisplayName("AUTH-14 : deux vérifications simultanées du bon code donnent un succès et un CODE_EXPIRED (A06)")
    void a06ConcurrentVerificationsSucceedOnce() throws Exception {
        Registered account = client.register("course.verif@exemple.ca", "course.verif");
        String code = client.codeSentTo("course.verif@exemple.ca");

        assertOneSuccessAndOneCodeExpired(
                race(List.of(verifyRequest(account.session(), code), verifyRequest(account.session(), code))));
    }

    @Test
    @DisplayName("AUTH-15 : deux comptes en attente à la même adresse vérifient en même temps : 200 et 422, jamais 500")
    void auth15ConcurrentVerificationsOfOneAddressSucceedOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            String address = "meme.adresse" + round + "@exemple.ca";
            AuthClient other = new AuthClient(mvc, mail);
            Registered first = client.register(address, "premier" + round);
            String firstCode = client.codeSentTo(address);
            Registered second = other.register(address, "second" + round);
            String secondCode = other.codeSentTo(address);

            assertOneSuccessAndOneCodeExpired(race(List.of(
                    verifyRequest(first.session(), firstCode),
                    other.unsafe(post("/api/v1/auth/verify-email"), second.session(), Map.of("code", secondCode)))));
            assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = ? AND status = 'ACTIVE'")
                            .param(address)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("AUTH-15 : l'activation d'une adresse ne change rien pour un autre compte en attente à cette adresse")
    void auth15ActivationLeavesTheOtherPendingAccountUnchanged() throws Exception {
        Registered spy = client.register("partagee@exemple.ca", "espion");
        String before = codeExpiresAt(spy.session());
        AuthClient owner = new AuthClient(mvc, mail);
        owner.verify(owner.register("Partagee@Exemple.ca", "proprietaire"));

        assertThat(codeExpiresAt(spy.session())).isEqualTo(before).isNotEqualTo("null");
        mvc.perform(verifyRequest(spy.session(), "000000"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }

    @Test
    @DisplayName(
            "AUTH-15 : le bon code d'une adresse activée entre-temps répond CODE_EXPIRED, et le compte reste en attente")
    void auth15RightCodeForAnAddressActivatedMeanwhileIsExpired() throws Exception {
        Registered late = client.register("doublon@exemple.ca", "retardataire");
        String lateCode = client.codeSentTo("doublon@exemple.ca");
        AuthClient owner = new AuthClient(mvc, mail);
        owner.verify(owner.register("doublon@exemple.ca", "titulaire.doublon"));

        mvc.perform(verifyRequest(late.session(), lateCode))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(late.session()))
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andExpect(jsonPath("$.verification.codeExpiresAt").value(nullValue()));
    }

    @Test
    @DisplayName("AUTH-14 : sans code vivant (épuisé ou expiré), /me n'annonce aucune expiration")
    void auth14NoLiveCodeMeansNoExpiry() throws Exception {
        Registered exhausted = client.register("epuise@exemple.ca", "epuise");
        String code = client.codeSentTo("epuise@exemple.ca");
        for (int i = 0; i < 5; i++) {
            mvc.perform(verifyRequest(exhausted.session(), wrongCode(code)));
        }
        Registered expired = new AuthClient(mvc, mail).register("perime@exemple.ca", "perime");

        assertThat(codeExpiresAt(exhausted.session())).isEqualTo("null");
        clock.advance(Duration.ofMinutes(10));
        assertThat(codeExpiresAt(expired.session())).isEqualTo("null");
    }

    @Test
    @DisplayName("QA-05 : vérification et changement refusent un champ inconnu ou un courriel invalide (400)")
    void qa05VerifyAndChangeRejectInvalidBodies() throws Exception {
        Registered account = client.register("corps@exemple.ca", "corps");
        List<Map<String, ?>> invalidChanges = List.of(
                Map.of("email", "pas-un-courriel"),
                Map.of("email", "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62)),
                Map.of("email", "nouvelle@exemple.ca", "status", "ACTIVE"));

        mvc.perform(client.unsafe(
                        post("/api/v1/auth/verify-email"), account.session(), Map.of("code", "123456", "extra", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        for (Map<String, ?> body : invalidChanges) {
            mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/change"), account.session(), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(account.session()))
                .andExpect(jsonPath("$.email").value("corps@exemple.ca"));
    }

    @Test
    @DisplayName("SEC-03 : la session d'un compte supprimé n'obtient ni son compte ni un renvoi (401)")
    void sec03DeletedAccountSessionGetsNothing() throws Exception {
        Registered account = client.register("supprime@exemple.ca", "supprime");
        jdbc.sql("UPDATE users SET status = 'DELETED', deleted_at = now() WHERE id = ?")
                .param(account.id())
                .update();

        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(account.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(post("/api/v1/auth/verify-email/resend"), account.session(), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AUTH-16 : la vérification révoque les autres sessions restreintes du compte (ADR 0021)")
    void auth16VerificationRevokesTheOtherRestrictedSessions() throws Exception {
        Registered account = client.register("deux.appareils@exemple.ca", "deux.appareils");
        AuthClient other = new AuthClient(mvc, mail);
        Cookie second = sessionOf(mvc.perform(other.unsafe(
                        post("/api/v1/auth/login"),
                        null,
                        Map.of("login", "deux.appareils", "password", AuthClient.PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn());

        Cookie full = client.verify(account);

        mvc.perform(other.from(get("/api/v1/auth/me")).cookie(second))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
