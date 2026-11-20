package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.OAuthClientProperties.Client;
import fr.vlegall.seal.support.FakeOAuthProvider;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubAccount;
import fr.vlegall.seal.support.FakeOAuthProvider.GitHubEmail;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Section 5.4 du CDC : l'identifiant de GitHub vient de /user, le courriel vérifié de /user/emails. */
@DisplayName("AUTH-31 : utilisateur GitHub")
class GitHubUserServiceTest {

    private static final Client CLIENT = new Client("github-client", "github-secret");
    private static final URI ORIGIN = URI.create("https://localhost:8443");

    private static FakeOAuthProvider fake;
    private static ClientRegistration registration;

    private final GitHubUserService service = new GitHubUserService();

    @BeforeAll
    static void startFake() {
        fake = FakeOAuthProvider.start();
        registration = fake.github(OAuthClientRegistrations.registration(OAuthProvider.GITHUB, CLIENT, ORIGIN))
                .build();
    }

    @AfterAll
    static void stopFake() {
        fake.close();
    }

    private static OAuth2UserRequest request(ClientRegistration client, String token) {
        Instant now = Instant.now();
        return new OAuth2UserRequest(
                client, new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token, now, now.plusSeconds(60)));
    }

    private OAuth2User load(String token, GitHubAccount account) {
        fake.githubAccount(token, account);
        return service.loadUser(request(registration, token));
    }

    @Test
    @DisplayName("AUTH-31 : id de /user, adresse principale vérifiée de /user/emails")
    void auth31PrimaryVerifiedEmail() {
        OAuth2User user = load(
                "jeton-principale",
                new GitHubAccount(
                        583231,
                        "octocat",
                        "The Octocat",
                        List.of(
                                new GitHubEmail("autre@exemple.ca", false, true),
                                new GitHubEmail("octocat@exemple.ca", true, true))));

        assertThat(user.getAttributes())
                .containsEntry("id", 583231)
                .containsEntry(GitHubUserService.PRIMARY_EMAIL, "octocat@exemple.ca")
                .containsEntry(GitHubUserService.PRIMARY_EMAIL_VERIFIED, true);
        assertThat(ExternalIdentities.from(OAuthProvider.GITHUB, user).subject())
                .isEqualTo("583231");
    }

    @Test
    @DisplayName("Section 5.4 : une adresse principale non vérifiée est rendue comme telle, même si une autre l'est")
    void cdc54PrimaryNotVerified() {
        OAuth2User user = load(
                "jeton-non-verifiee",
                new GitHubAccount(
                        42,
                        "octo",
                        null,
                        List.of(
                                new GitHubEmail("verifiee@exemple.ca", false, true),
                                new GitHubEmail("principale@exemple.ca", true, false))));

        assertThat(user.getAttributes())
                .containsEntry(GitHubUserService.PRIMARY_EMAIL, "principale@exemple.ca")
                .containsEntry(GitHubUserService.PRIMARY_EMAIL_VERIFIED, false);
    }

    @Test
    @DisplayName("AUTH-31 : sans adresse principale, l'identité GitHub est refusée")
    void auth31NoPrimaryEmailRefused() {
        fake.githubAccount(
                "jeton-sans-principale",
                new GitHubAccount(7, "octo", null, List.of(new GitHubEmail("a@exemple.ca", false, true))));

        assertThatThrownBy(() -> service.loadUser(request(registration, "jeton-sans-principale")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("A10 : /user/emails en erreur, ou jeton refusé par GitHub : échec fermé")
    void a10ProviderErrorsFailClosed() {
        fake.githubAccount("jeton-panne", new GitHubAccount(8, "octo", null, null));

        assertThatThrownBy(() -> service.loadUser(request(registration, "jeton-panne")))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service.loadUser(request(registration, "jeton-inconnu")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("AUTH-31 : un id GitHub en texte est refusé dès le chargement")
    void auth31TextIdRefused() {
        fake.githubAccount(
                "jeton-id-texte",
                new GitHubAccount("583231", "octo", null, List.of(new GitHubEmail("o@exemple.ca", true, true))));

        assertThatThrownBy(() -> service.loadUser(request(registration, "jeton-id-texte")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("AUTH-30 : ce service ne charge que GitHub")
    void auth30OnlyGitHub() {
        ClientRegistration google = fake.google(
                        OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, CLIENT, ORIGIN))
                .build();

        assertThatThrownBy(() -> service.loadUser(request(google, "jeton-principale")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
