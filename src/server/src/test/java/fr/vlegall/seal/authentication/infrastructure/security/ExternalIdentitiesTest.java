package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** AUTH-31, section 5.4 du CDC : identité stable et courriel vérifié, lus strictement chez chaque fournisseur. */
@DisplayName("AUTH-31 : lecture de l'identité Google et GitHub")
class ExternalIdentitiesTest {

    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("OIDC_USER"));

    private static OAuth2User google(Map<String, Object> claims) {
        OidcIdToken.Builder token = OidcIdToken.withTokenValue("jeton")
                .issuedAt(Instant.parse("2026-11-20T12:00:00Z"))
                .expiresAt(Instant.parse("2026-11-20T13:00:00Z"));
        claims.forEach(token::claim);
        return new DefaultOidcUser(AUTHORITIES, token.build());
    }

    private static Map<String, Object> googleClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "109876543210");
        claims.put("email", "valentin.dupont@gmail.com");
        claims.put("email_verified", true);
        claims.put("name", "Valentin Dupont");
        return claims;
    }

    private static OAuth2User github(Map<String, Object> attributes) {
        return new DefaultOAuth2User(AUTHORITIES, attributes, "login");
    }

    private static Map<String, Object> githubAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 583231);
        attributes.put("login", "octocat");
        attributes.put("name", "The Octocat");
        attributes.put(GitHubUserService.PRIMARY_EMAIL, "octocat@exemple.ca");
        attributes.put(GitHubUserService.PRIMARY_EMAIL_VERIFIED, true);
        return attributes;
    }

    @Test
    @DisplayName("AUTH-31 : Google donne sub, courriel, email_verified et nom")
    void auth31Google() {
        ExternalIdentity identity = ExternalIdentities.from(OAuthProvider.GOOGLE, google(googleClaims()));

        assertThat(identity.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identity.subject()).isEqualTo("109876543210");
        assertThat(identity.email()).isEqualTo("valentin.dupont@gmail.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.displayName()).isEqualTo("Valentin Dupont");
    }

    @Test
    @DisplayName("Section 5.4 : seul email_verified booléen vrai vaut vérifié ; absent, faux ou en texte, non vérifié")
    void cdc54GoogleEmailVerifiedReadStrictly() {
        Map<String, Object> absent = googleClaims();
        absent.remove("email_verified");
        Map<String, Object> text = googleClaims();
        text.put("email_verified", "true");
        Map<String, Object> no = googleClaims();
        no.put("email_verified", false);

        for (Map<String, Object> claims : List.of(absent, text, no)) {
            assertThat(ExternalIdentities.from(OAuthProvider.GOOGLE, google(claims))
                            .emailVerified())
                    .as(claims.toString())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("AUTH-31 : sans courriel, ou avec un courriel mal formé, l'identité Google est refusée")
    void auth31GoogleWithoutEmailRefused() {
        Map<String, Object> missing = googleClaims();
        missing.remove("email");
        Map<String, Object> malformed = googleClaims();
        malformed.put("email", "pas-un-courriel");
        Map<String, Object> notText = googleClaims();
        notText.put("email", List.of("valentin.dupont@gmail.com"));

        for (Map<String, Object> claims : List.of(missing, malformed, notText)) {
            assertThatThrownBy(() -> ExternalIdentities.from(OAuthProvider.GOOGLE, google(claims)))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
    }

    @Test
    @DisplayName("AUTH-31 : GitHub donne l'id numérique en texte décimal, l'adresse principale et le nom")
    void auth31GitHub() {
        ExternalIdentity identity = ExternalIdentities.from(OAuthProvider.GITHUB, github(githubAttributes()));

        assertThat(identity.provider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(identity.subject()).isEqualTo("583231");
        assertThat(identity.email()).isEqualTo("octocat@exemple.ca");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.displayName()).isEqualTo("The Octocat");

        Map<String, Object> large = githubAttributes();
        large.put("id", 9_000_000_000L);
        assertThat(ExternalIdentities.from(OAuthProvider.GITHUB, github(large)).subject())
                .isEqualTo("9000000000");
    }

    @Test
    @DisplayName("AUTH-31 : un id GitHub en texte, nul, négatif ou décimal est refusé")
    void auth31GitHubIdReadStrictly() {
        for (Object id : new Object[] {"583231", 0, -5, 1.5}) {
            Map<String, Object> attributes = githubAttributes();
            attributes.put("id", id);
            assertThatThrownBy(() -> ExternalIdentities.from(OAuthProvider.GITHUB, github(attributes)))
                    .as(String.valueOf(id))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
    }

    @Test
    @DisplayName("Section 5.4 : adresse principale non vérifiée, ou vérification en texte, vaut non vérifié")
    void cdc54GitHubVerificationReadStrictly() {
        Map<String, Object> no = githubAttributes();
        no.put(GitHubUserService.PRIMARY_EMAIL_VERIFIED, false);
        Map<String, Object> text = githubAttributes();
        text.put(GitHubUserService.PRIMARY_EMAIL_VERIFIED, "true");

        assertThat(ExternalIdentities.from(OAuthProvider.GITHUB, github(no)).emailVerified())
                .isFalse();
        assertThat(ExternalIdentities.from(OAuthProvider.GITHUB, github(text)).emailVerified())
                .isFalse();
    }

    @Test
    @DisplayName("ACC-03 : sans nom, GitHub propose le login")
    void acc03GitHubNameFallsBackToLogin() {
        Map<String, Object> attributes = githubAttributes();
        attributes.remove("name");

        assertThat(ExternalIdentities.from(OAuthProvider.GITHUB, github(attributes))
                        .displayName())
                .isEqualTo("octocat");
    }

    @Test
    @DisplayName("AUTH-34 : Microsoft n'est pas encore lu (P1)")
    void auth34MicrosoftNotYet() {
        assertThatThrownBy(() -> ExternalIdentities.from(OAuthProvider.MICROSOFT, google(googleClaims())))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
