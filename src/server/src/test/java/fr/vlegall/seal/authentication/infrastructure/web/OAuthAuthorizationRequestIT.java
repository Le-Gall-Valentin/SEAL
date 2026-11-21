package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.OAuthTestConfiguration;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/** AUTH-30, AUTH-35 : départ vers Google et GitHub, avec PKCE S256, state et nonce (RFC 9700). */
@SealIntegrationTest
@Import(OAuthTestConfiguration.class)
class OAuthAuthorizationRequestIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private FakeOAuthProvider fake;

    private AuthClient client;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    /** Paramètres de la redirection vers le fournisseur, décodés. */
    private static Map<String, String> query(String location) {
        return UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .toSingleValueMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
    }

    private MvcResult start(String provider) throws Exception {
        return mvc.perform(client.from(get("/oauth2/authorization/" + provider)))
                .andExpect(status().isFound())
                .andReturn();
    }

    @Test
    @DisplayName("AUTH-35 : GET /auth/providers rend les fournisseurs configurés, dans l'ordre de la maquette")
    void auth35ConfiguredProviders() throws Exception {
        mvc.perform(client.from(get("/api/v1/auth/providers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[0]").value("google"))
                .andExpect(jsonPath("$.providers[1]").value("github"));
    }

    @Test
    @DisplayName("AUTH-30 : Google reçoit code, state, nonce et PKCE S256, avec la redirection exacte d'AUTH-35")
    void auth30GoogleAuthorizationRequest() throws Exception {
        MvcResult result = start("google");

        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith(fake.uri("/google/authorize") + "?");
        Map<String, String> query = query(location);
        assertThat(query)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", OAuthTestConfiguration.GOOGLE.clientId())
                .containsEntry("redirect_uri", "https://localhost:8443/login/oauth2/code/google")
                .containsEntry("code_challenge_method", "S256");
        assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("nonce")).isNotBlank();
        assertThat(query.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.getResponse().getCookie(AuthClient.SESSION_COOKIE))
                .as("la requête d'autorisation est gardée dans une session")
                .isNotNull();
    }

    @Test
    @DisplayName("AUTH-30 : GitHub reçoit code, state et PKCE S256, sans nonce (OAuth 2.0 sans OIDC)")
    void auth30GitHubAuthorizationRequest() throws Exception {
        String location = start("github").getResponse().getHeader("Location");

        assertThat(location).startsWith(fake.uri("/github/authorize") + "?");
        Map<String, String> query = query(location);
        assertThat(query)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", OAuthTestConfiguration.GITHUB.clientId())
                .containsEntry("redirect_uri", "https://localhost:8443/login/oauth2/code/github")
                .containsEntry("code_challenge_method", "S256")
                .doesNotContainKey("nonce");
        assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("read:user", "user:email");
        assertThat(query.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("AUTH-30 : chaque départ tire un state, un nonce et un défi PKCE neufs")
    void auth30FreshValuesForEachStart() throws Exception {
        Map<String, String> first = query(start("google").getResponse().getHeader("Location"));
        Map<String, String> second = query(start("google").getResponse().getHeader("Location"));

        assertThat(second.get("state")).isNotEqualTo(first.get("state"));
        assertThat(second.get("nonce")).isNotEqualTo(first.get("nonce"));
        assertThat(second.get("code_challenge")).isNotEqualTo(first.get("code_challenge"));
    }

    @Test
    @DisplayName("AUTH-30 : une session ouverte ne repart pas vers un fournisseur : 403 FORBIDDEN, sans redirection")
    void auth30SignedInSessionCannotStart() throws Exception {
        String name = "oa" + UUID.randomUUID().toString().substring(0, 8);
        Registered account = client.register(name + "@exemple.ca", name);
        Cookie session = client.verify(account);

        mvc.perform(client.from(get("/oauth2/authorization/google")).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
