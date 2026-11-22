package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GoogleAccount;
import fr.vlegall.seal.support.OAuthFlow;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** ADR 0035, point 2 : {@code GET /api/v1/account/identities}, de bout en bout dans le contexte complet. */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class AccountIdentitiesIT {

    private static final String IDENTITIES = "/api/v1/account/identities";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    @Autowired
    private OAuthIdentityRepository identities;

    private AuthClient client;
    private OAuthFlow oauth;

    /** Suffixe propre à ce test : la base est partagée par toute la série des tests d'intégration. */
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
        oauth = new OAuthFlow(mvc, client, fake);
    }

    @AfterEach
    void reset() {
        mail.reset();
        fake.reset();
    }

    private static String googleSubject() {
        return "3" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : compte par mot de passe, sans fournisseur lié ; aucun autre champ")
    void auth55PasswordAccount() throws Exception {
        Cookie full = client.verify(client.register("methodes" + run + "@exemple.ca", "methodes" + run));

        mvc.perform(client.from(get(IDENTITIES)).cookie(full))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"passwordSet\":true,\"providers\":[]}", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : compte par mot de passe lié à Google")
    void auth55PasswordAndLinkedProvider() throws Exception {
        Registered account = client.register("liee" + run + "@exemple.ca", "liee" + run);
        Cookie full = client.verify(account);
        identities.link(
                new UserId(account.id()),
                ExternalIdentity.of(OAuthProvider.GOOGLE, googleSubject(), account.email(), true, "Nom"),
                Instant.now());

        mvc.perform(client.from(get(IDENTITIES)).cookie(full))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"passwordSet\":true,\"providers\":[{\"provider\":\"google\"}]}",
                                JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : compte créé par Google, sans mot de passe")
    void auth55OAuthOnlyAccount() throws Exception {
        String email = "oauth" + run + "@exemple.ca";
        MvcResult signIn = oauth.signIn("google", GoogleAccount.verified(googleSubject(), email));
        assertThat(signIn.getResponse().getHeader("Location")).isEqualTo("/inscription/identifiant");
        MvcResult completed = mvc.perform(client.unsafe(
                        post("/api/v1/auth/oauth/complete"),
                        AuthClient.sessionOf(signIn),
                        Map.of("username", "oauth" + run, "displayName", "Nom")))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(client.from(get(IDENTITIES)).cookie(AuthClient.sessionOf(completed)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"passwordSet\":false,\"providers\":[{\"provider\":\"google\"}]}",
                                JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("QA-05 : 401 sans session, 403 pour une session restreinte")
    void qa05AccessRules() throws Exception {
        Registered pending = client.register("restreint" + run + "@exemple.ca", "restreint" + run);

        mvc.perform(client.from(get(IDENTITIES)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.from(get(IDENTITIES)).cookie(pending.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
