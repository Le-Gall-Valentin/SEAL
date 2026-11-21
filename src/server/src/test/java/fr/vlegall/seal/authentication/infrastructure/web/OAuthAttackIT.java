package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubAccount;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubEmail;
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthFlow.Started;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AUTH-30, SEC-10, section 16.1 : retours OAuth forgés et départs en rafale. Chaque retour forgé finit sur
 * {@code /connexion?erreur=REQUEST_REJECTED}, sans session ouverte.
 */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthAttackIT {

    private static final String REJECTED = "/connexion?erreur=REQUEST_REJECTED";
    private static final AtomicLong GITHUB_IDS = new AtomicLong(9_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    @Autowired
    private JdbcClient jdbc;

    private AuthClient client;
    private OAuthFlow oauth;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
        oauth = new OAuthFlow(mvc, client, fake);
    }

    @AfterEach
    void resetFake() {
        fake.reset();
    }

    private static GoogleAccount google() {
        return GoogleAccount.verified("3" + UUID.randomUUID().toString().replaceAll("[^0-9]", ""), "attaque@gmail.com");
    }

    /** Le retour est refusé, et la session qu'il laisse, s'il en laisse une, n'est pas authentifiée. */
    private void assertRejected(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(REJECTED);
        var session = result.getResponse().getCookie(AuthClient.SESSION_COOKIE);
        if (session != null) {
            mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("AUTH-30 : un jeton d'identité destiné à un autre client est refusé")
    void auth30OtherAudienceRejected() throws Exception {
        fake.tweakIdTokens(claims -> claims.audience("client-d-un-autre-site"));

        assertRejected(oauth.signIn("google", google()));
    }

    @Test
    @DisplayName("AUTH-30 : un jeton d'identité d'un autre émetteur est refusé")
    void auth30OtherIssuerRejected() throws Exception {
        fake.tweakIdTokens(claims -> claims.issuer("https://emetteur.attaquant.example"));

        assertRejected(oauth.signIn("google", google()));
    }

    @Test
    @DisplayName("AUTH-30 : un jeton d'identité expiré est refusé")
    void auth30ExpiredIdTokenRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        fake.tweakIdTokens(
                claims -> claims.issueTime(Date.from(past)).expirationTime(Date.from(past.plusSeconds(300))));

        assertRejected(oauth.signIn("google", google()));
    }

    @Test
    @DisplayName("AUTH-30 : un jeton d'identité signé par une clé absente des JWKS est refusé")
    void auth30StrangerKeyRejected() throws Exception {
        fake.signWithStrangerKey(true);

        assertRejected(oauth.signIn("google", google()));
    }

    @Test
    @DisplayName("AUTH-30 : une adresse GitHub sans adresse principale est refusée")
    void auth30GitHubWithoutPrimaryEmailRejected() throws Exception {
        long id = GITHUB_IDS.incrementAndGet();

        assertRejected(oauth.signIn(
                "github", new GitHubAccount(id, "octo", null, List.of(new GitHubEmail("o@exemple.ca", false, true)))));
    }

    @Test
    @DisplayName(
            "SEC-20, SEC-21 : consentement refusé journalisé avec son code ; un code forgé ne passe pas au journal")
    void sec20ProviderErrorsJournaledSafely() throws Exception {
        Started refused = oauth.start("google");
        assertRejected(mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("error", "access_denied")
                                .param("state", refused.state()))
                        .cookie(refused.session()))
                .andReturn());

        Started forged = oauth.start("google");
        String injected = "x\nLOGIN_SUCCEEDED <script>";
        assertRejected(mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("error", injected)
                                .param("state", forged.state()))
                        .cookie(forged.session()))
                .andReturn());

        List<String> causes = jdbc.sql("SELECT details->>'cause' FROM security_events WHERE type = 'LOGIN_FAILED' "
                        + "AND details->>'method' = 'oauth' AND ip = CAST(? AS inet)")
                .param(client.ip())
                .query(String.class)
                .list();
        assertThat(causes).containsExactlyInAnyOrder("access_denied", "oauth_error");
        assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE details::text LIKE '%script%'")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0032 : 20 départs OAuth par 15 minutes et par IP, puis retour vers la connexion avec RATE_LIMITED")
    void sec10OAuthStartsLimitedPerIp() throws Exception {
        AuthClient burst = new AuthClient(mvc, mail);
        for (int i = 0; i < 20; i++) {
            mvc.perform(burst.from(get("/oauth2/authorization/google"))).andExpect(status().isFound());
        }

        MvcResult limited = mvc.perform(burst.from(get("/oauth2/authorization/google")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/connexion?erreur=RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        assertThat(limited.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                .as("aucune session créée pour un départ refusé")
                .isNull();
        mvc.perform(client.from(get("/oauth2/authorization/google"))).andExpect(status().isFound());
    }

    @Test
    @DisplayName("SEC-10, A06 : un départ OAuth autre que GET ne redirige pas et ne crée aucune session")
    void sec10NonGetStartCreatesNoSession() throws Exception {
        MvcResult head =
                mvc.perform(client.from(head("/oauth2/authorization/google"))).andReturn();
        MvcResult post = mvc.perform(client.unsafe(post("/oauth2/authorization/google"), null, null))
                .andReturn();

        for (MvcResult result : List.of(head, post)) {
            assertThat(result.getResponse().getStatus()).isNotEqualTo(302);
            assertThat(result.getResponse().getHeader("Location")).isNull();
            assertThat(result.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                    .isNull();
        }
    }

    @Test
    @DisplayName("SEC-10, A06 : la limite des départs compte toute méthode, pas seulement GET")
    void sec10LimitCountsEveryMethod() throws Exception {
        AuthClient burst = new AuthClient(mvc, mail);
        for (int i = 0; i < 20; i++) {
            mvc.perform(burst.from(head("/oauth2/authorization/google")));
        }

        mvc.perform(burst.from(get("/oauth2/authorization/google")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/connexion?erreur=RATE_LIMITED"));
    }

    @Test
    @DisplayName("SEC-10, ADR 0032 : 20 retours OAuth par 15 minutes et par IP ; au-delà, ni journal ni traitement")
    void sec10OAuthCallbacksLimitedPerIp() throws Exception {
        AuthClient burst = new AuthClient(mvc, mail);
        for (int i = 0; i < 20; i++) {
            mvc.perform(burst.from(get("/login/oauth2/code/google")
                            .param("code", "c" + i)
                            .param("state", "s" + i)))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", REJECTED));
        }

        mvc.perform(burst.from(
                        get("/login/oauth2/code/github").param("code", "c").param("state", "s")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/connexion?erreur=RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        assertThat(jdbc.sql("SELECT type, count(*) FROM security_events WHERE ip = CAST(? AS inet) GROUP BY type")
                        .param(burst.ip())
                        .query((row, number) -> row.getString(1) + "=" + row.getLong(2))
                        .list())
                .as("20 refus journalisés, puis un seul dépassement")
                .containsExactlyInAnyOrder("LOGIN_FAILED=20", "RATE_LIMIT_EXCEEDED=1");
        mvc.perform(burst.from(get("/oauth2/authorization/google")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(fake.uri("/google/authorize"))));
    }

    /** Une sous-requête qu'une page tierce émet depuis le navigateur de la victime : une balise img, sans cookie. */
    private static MockHttpServletRequestBuilder crossSiteImage(MockHttpServletRequestBuilder request) {
        return request.header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Dest", "image");
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0032 : des sous-requêtes intersites n'épuisent pas les limites OAuth de l'IP de la victime")
    void sec10CrossSiteSubrequestsDoNotSpendTheLimits() throws Exception {
        AuthClient victim = new AuthClient(mvc, mail);
        for (int i = 0; i < 25; i++) {
            for (MockHttpServletRequestBuilder request : List.of(
                    get("/oauth2/authorization/google"),
                    get("/login/oauth2/code/google").param("code", "c" + i).param("state", "s" + i))) {
                MvcResult refused = mvc.perform(victim.from(crossSiteImage(request)))
                        .andExpect(status().isFound())
                        .andExpect(header().string("Location", REJECTED))
                        .andReturn();
                assertThat(refused.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                        .as("aucune session créée")
                        .isNull();
            }
        }

        assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE ip = CAST(? AS inet)")
                        .param(victim.ip())
                        .query(Long.class)
                        .single())
                .as("rien n'est journalisé")
                .isZero();
        mvc.perform(victim.from(get("/oauth2/authorization/google")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Dest", "document")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(fake.uri("/google/authorize"))));
        mvc.perform(victim.from(get("/login/oauth2/code/google")
                        .param("code", "c")
                        .param("state", "s")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Dest", "document")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", REJECTED));
        assertThat(jdbc.sql("SELECT count(*) FROM security_events WHERE ip = CAST(? AS inet) AND type = 'LOGIN_FAILED'")
                        .param(victim.ip())
                        .query(Long.class)
                        .single())
                .as("le retour du fournisseur, navigation intersite, est traité")
                .isOne();
    }

    @Test
    @DisplayName(
            "SEC-10, TRN-02 : un départ OAuth venu d'une autre origine est refusé, sans session ; depuis SEAL, il part")
    void sec10StartFromAnotherOriginIsRejected() throws Exception {
        for (String site : List.of("cross-site", "same-site")) {
            MvcResult refused = mvc.perform(client.from(get("/oauth2/authorization/github")
                            .header("Sec-Fetch-Site", site)
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Dest", "document")))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", REJECTED))
                    .andReturn();
            assertThat(refused.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                    .isNull();
        }
        for (String site : List.of("same-origin", "none")) {
            mvc.perform(client.from(get("/oauth2/authorization/github")
                            .header("Sec-Fetch-Site", site)
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Dest", "document")))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", startsWith(fake.uri("/github/authorize"))));
        }
    }

    /** Les causes des refus journalisés pour l'IP de ce client, dans l'ordre. */
    private List<String> causes(AuthClient who) {
        return jdbc.sql("SELECT details->>'cause' FROM security_events WHERE type = 'LOGIN_FAILED' "
                        + "AND ip = CAST(? AS inet) ORDER BY created_at, id")
                .param(who.ip())
                .query(String.class)
                .list();
    }

    @Test
    @DisplayName("SEC-20 : un vrai code serveur garde son nom au journal, même quand le retour recopie error")
    void sec20ServerErrorCodesKeepTheirNameEvenWithAnEchoedError() throws Exception {
        fake.tweakIdTokens(claims -> claims.claim("nonce", "nonce-d-un-autre-depart"));
        Started plain = oauth.start("google");
        oauth.callback(plain, fake.authorize(plain.location(), google()));
        Started echoed = oauth.start("google");
        String code = fake.authorize(echoed.location(), google());
        mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("code", code)
                                .param("state", echoed.state())
                                .param("error", "invalid_nonce"))
                        .cookie(echoed.session()))
                .andExpect(header().string("Location", REJECTED));
        Started unknownCode = oauth.start("google");
        mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("code", "code-inconnu")
                                .param("state", unknownCode.state())
                                .param("error", "invalid_grant"))
                        .cookie(unknownCode.session()))
                .andExpect(header().string("Location", REJECTED));

        assertThat(causes(client)).containsExactly("invalid_nonce", "invalid_nonce", "invalid_grant");
    }

    @Test
    @DisplayName("SEC-20 : un retour sans code ni erreur est journalisé invalid_request, levé par Spring")
    void sec20CallbackWithoutCodeNorErrorIsInvalidRequest() throws Exception {
        Started started = oauth.start("google");
        mvc.perform(client.from(get("/login/oauth2/code/google")
                                .param("error", "")
                                .param("state", started.state()))
                        .cookie(started.session()))
                .andExpect(header().string("Location", REJECTED));

        assertThat(causes(client)).containsExactly("invalid_request");
    }

    /** Nombre d'événements du journal pour l'IP de ce client. */
    private long events(AuthClient who) {
        return jdbc.sql("SELECT count(*) FROM security_events WHERE ip = CAST(? AS inet)")
                .param(who.ip())
                .query(Long.class)
                .single();
    }

    @ParameterizedTest(name = "Sec-Fetch-Dest: \"{0}\", Sec-Fetch-Mode: \"{1}\"")
    @CsvSource({"iframe, navigate", "document, no-cors", "document, cors", "'', navigate", "document, ''"})
    @DisplayName("SEC-10, ADR 0032 : chaque règle de Fetch Metadata écarte seule un retour, sans le compter")
    void sec10EachFetchMetadataRuleRejectsAlone(String dest, String mode) throws Exception {
        AuthClient victim = new AuthClient(mvc, mail);
        for (int i = 0; i < 21; i++) {
            mvc.perform(victim.from(get("/login/oauth2/code/google")
                            .param("code", "c" + i)
                            .param("state", "s" + i)
                            .header("Sec-Fetch-Site", "cross-site")
                            .header("Sec-Fetch-Dest", dest)
                            .header("Sec-Fetch-Mode", mode)))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", REJECTED));
        }

        assertThat(events(victim)).as("ni journal ni dépassement").isZero();
    }

    @Test
    @DisplayName(
            "SEC-10, ADR 0032 : un préchargement ou un prérendu est refusé par un 403 sans corps, sans être compté")
    void sec10SpeculativeRequestsAreRefusedUncounted() throws Exception {
        AuthClient victim = new AuthClient(mvc, mail);
        for (int i = 0; i < 25; i++) {
            for (MockHttpServletRequestBuilder request : List.of(
                    get("/login/oauth2/code/google")
                            .param("code", "c" + i)
                            .param("state", "s" + i)
                            .header("Sec-Fetch-Site", "cross-site")
                            .header("Sec-Purpose", "prefetch"),
                    get("/oauth2/authorization/google")
                            .header("Sec-Fetch-Site", "same-origin")
                            .header("Sec-Purpose", "prefetch;prerender"))) {
                MvcResult refused = mvc.perform(victim.from(
                                request.header("Sec-Fetch-Mode", "navigate").header("Sec-Fetch-Dest", "document")))
                        .andExpect(status().isForbidden())
                        .andExpect(header().doesNotExist("Location"))
                        .andReturn();
                assertThat(refused.getResponse().getContentAsByteArray()).isEmpty();
                assertThat(refused.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                        .isNull();
            }
        }

        assertThat(events(victim)).isZero();
        mvc.perform(victim.from(get("/login/oauth2/code/google")
                        .param("code", "c")
                        .param("state", "s")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Dest", "document")))
                .andExpect(header().string("Location", REJECTED));
        mvc.perform(victim.from(get("/oauth2/authorization/google")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Dest", "document")))
                .andExpect(header().string("Location", startsWith(fake.uri("/google/authorize"))));
    }
}
