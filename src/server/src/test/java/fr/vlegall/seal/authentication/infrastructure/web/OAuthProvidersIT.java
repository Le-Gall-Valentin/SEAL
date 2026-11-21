package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * OPS-03 : SEAL démarre et fonctionne sans aucune variable OAuth. Le contexte partagé des tests d'intégration n'en
 * configure aucune.
 */
@SealIntegrationTest
class OAuthProvidersIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    private AuthClient client;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @Test
    @DisplayName("OPS-03, AUTH-35 : sans application configurée, GET /auth/providers rend une liste vide")
    void ops03NoProvider() throws Exception {
        mvc.perform(client.from(get("/api/v1/auth/providers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isArray())
                .andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    @DisplayName("OPS-03 : le départ vers un fournisseur non configuré répond 404 NOT_FOUND, sans session créée")
    void ops03UnconfiguredProviderIsNotFound() throws Exception {
        for (String provider : new String[] {"google", "github", "microsoft", "okta"}) {
            MvcResult result = mvc.perform(client.from(get("/oauth2/authorization/" + provider)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andReturn();
            assertThat(result.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                    .as(provider)
                    .isNull();
        }
    }

    @Test
    @DisplayName("AUTH-30 : un retour OAuth sans départ renvoie vers /connexion avec REQUEST_REJECTED")
    void auth30CallbackWithoutRequestIsRejected() throws Exception {
        mvc.perform(client.from(
                        get("/login/oauth2/code/google").param("code", "abc").param("state", "xyz")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/connexion?erreur=REQUEST_REJECTED"));
    }

    @Test
    @DisplayName("OPS-03, A02 : OAuth n'ajoute aucune page de connexion générée par Spring")
    void a02NoGeneratedLoginPage() throws Exception {
        MvcResult result = mvc.perform(client.from(get("/login").accept(MediaType.TEXT_HTML)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<form");
    }

    @Test
    @DisplayName("QA-05 : un 401 de l'API reste en Problem Details, jamais une redirection vers la connexion")
    void qa05ApiStillAnswersProblemDetails() throws Exception {
        mvc.perform(client.from(get("/api/v1/auth/me").accept(MediaType.TEXT_HTML)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AUTH-35, QA-05 : une session complète lit aussi les fournisseurs (paramètres, liaison au P1)")
    void auth35FullSessionReadsProviders() throws Exception {
        String name = "pr" + UUID.randomUUID().toString().substring(0, 8);
        Cookie session = client.verify(client.register(name + "@exemple.ca", name));

        mvc.perform(client.from(get("/api/v1/auth/providers")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isArray());
    }
}
