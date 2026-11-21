package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.OAuthClientProperties;
import fr.vlegall.seal.common.OAuthClientProperties.Client;
import fr.vlegall.seal.common.OAuthClientProperties.Pair;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/** AUTH-30, AUTH-35, OPS-03 : inscriptions OAuth de Google et GitHub (section 5.4 du CDC). */
@DisplayName("AUTH-30 : inscriptions OAuth des fournisseurs")
class OAuthClientRegistrationsTest {

    private static final URI ORIGIN = URI.create("https://localhost:8443");
    private static final Client CLIENT = new Client("client-id", "client-secret");

    private static OAuthClientRegistrations from(OAuthClientProperties properties) {
        return OAuthClientRegistrations.from(properties, ORIGIN);
    }

    @Test
    @DisplayName("OPS-03 : aucune application configurée, aucun fournisseur, aucune inscription")
    void ops03NoProvider() {
        OAuthClientRegistrations registrations = from(new OAuthClientProperties(null, null));

        assertThat(registrations.configured()).isEmpty();
        assertThat(registrations.findByRegistrationId("google")).isNull();
        assertThat(registrations.findByRegistrationId("github")).isNull();
    }

    @Test
    @DisplayName("AUTH-30 : Google en OIDC, portées openid email profile, client confidentiel avec PKCE")
    void auth30Google() {
        ClientRegistration google = OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN)
                .build();

        assertThat(google.getRegistrationId()).isEqualTo("google");
        assertThat(google.getClientId()).isEqualTo("client-id");
        assertThat(google.getClientSecret()).isEqualTo("client-secret");
        assertThat(google.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(google.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(google.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(google.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(google.getProviderDetails().getJwkSetUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        assertThat(google.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
    }

    @Test
    @DisplayName("AUTH-30 : GitHub en OAuth 2.0, portées read:user user:email, identifiant id, avec PKCE")
    void auth30GitHub() {
        ClientRegistration github = OAuthClientRegistrations.registration(OAuthProvider.GITHUB, CLIENT, ORIGIN)
                .build();

        assertThat(github.getRegistrationId()).isEqualTo("github");
        assertThat(github.getScopes()).containsExactlyInAnyOrder("read:user", "user:email");
        assertThat(github.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(github.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(github.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://api.github.com/user");
        assertThat(github.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("id");
    }

    @Test
    @DisplayName("AUTH-35 : redirection exacte, fixée par l'origine et non par l'en-tête Host de la requête")
    void auth35RedirectUriFromTheOrigin() {
        assertThat(OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN)
                        .build()
                        .getRedirectUri())
                .isEqualTo("https://localhost:8443/login/oauth2/code/google");
        assertThat(OAuthClientRegistrations.registration(OAuthProvider.GITHUB, CLIENT, ORIGIN)
                        .build()
                        .getRedirectUri())
                .isEqualTo("https://localhost:8443/login/oauth2/code/github");
    }

    @Test
    @DisplayName("AUTH-35 : fournisseurs configurés dans l'ordre de la maquette, inscription retrouvée par son id")
    void auth35ConfiguredInMockupOrder() {
        OAuthClientRegistrations registrations =
                from(new OAuthClientProperties(new Pair("g-id", "g-secret"), new Pair("h-id", "h-secret")));

        assertThat(registrations.configured()).containsExactly(OAuthProvider.GOOGLE, OAuthProvider.GITHUB);
        assertThat(registrations.findByRegistrationId("google").getClientId()).isEqualTo("g-id");
        assertThat(registrations.findByRegistrationId("github").getClientId()).isEqualTo("h-id");
        assertThat(registrations.findByRegistrationId("microsoft")).isNull();
        assertThat(registrations.findByRegistrationId("GOOGLE")).isNull();
    }

    @Test
    @DisplayName("AUTH-35 : GitHub seul est configuré sans Google")
    void auth35GitHubAlone() {
        OAuthClientRegistrations registrations = from(new OAuthClientProperties(null, new Pair("h-id", "h-secret")));

        assertThat(registrations.configured()).containsExactly(OAuthProvider.GITHUB);
    }

    @Test
    @DisplayName("AUTH-30 : une inscription sans PKCE, d'un fournisseur inconnu ou en double est refusée")
    void auth30InconsistentRegistrationsRefused() {
        ClientRegistration withoutPkce = OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN)
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(false)
                        .build())
                .build();
        ClientRegistration google = OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN)
                .build();
        ClientRegistration unknown = OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN)
                .registrationId("okta")
                .build();

        assertThatThrownBy(() -> new OAuthClientRegistrations(List.of(withoutPkce)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthClientRegistrations(List.of(google, google)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthClientRegistrations(List.of(unknown)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AUTH-34 : Microsoft n'est pas encore pris en charge (P1)")
    void auth34MicrosoftNotYet() {
        assertThatThrownBy(() -> OAuthClientRegistrations.registration(OAuthProvider.MICROSOFT, CLIENT, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
