package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.application.port.in.OAuthReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.session.SessionMigration;
import fr.vlegall.seal.security.web.ClientRoutesController;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * AUTH-30 à AUTH-33 : défenses du retour OAuth réussi chez le fournisseur. Les cas nominaux sont couverts par
 * {@code OAuthLoginIT} ; ici, les retours qu'aucun parcours normal ne produit doivent finir en refus, sans session.
 */
@DisplayName("AUTH-30 : retour OAuth réussi, cas défensifs")
class OAuthLoginSuccessHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private final List<String> rejections = new ArrayList<>();
    private OAuthLoginOutcome outcome = new OAuthLoginOutcome.Rejected();

    private final OAuthLoginUseCase login = new OAuthLoginUseCase() {
        @Override
        public OAuthLoginOutcome resolve(ExternalIdentity identity) {
            return outcome;
        }

        @Override
        public void rejected(@Nullable OAuthProvider provider, String cause) {
            rejections.add(provider + ":" + cause);
        }
    };

    private final MutableClock clock = MutableClock.fixedAt(NOW);
    private final OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
            login,
            // Un retour de connexion ne passe jamais par la ré-authentification (ADR 0034).
            new OAuthReauthenticateUseCase() {
                @Override
                public boolean canStart(UserId user, OAuthProvider provider) {
                    throw new AssertionError("ré-authentification inattendue");
                }

                @Override
                public boolean reauthenticate(UserId user, ExternalIdentity identity) {
                    throw new AssertionError("ré-authentification inattendue");
                }
            },
            new SessionEstablisher(
                    CookieCsrfTokenRepository.withHttpOnlyFalse(),
                    clock,
                    user -> OptionalLong.empty(),
                    List.of(),
                    event -> {},
                    new SessionMigration(event -> {}, id -> true)),
            new PendingOAuthSession(clock));

    private MockHttpServletResponse handle(Authentication authentication) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);
        return response;
    }

    private static OAuth2AuthenticationToken token(String registrationId, Map<String, Object> attributes) {
        DefaultOAuth2User user =
                new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "id");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
    }

    @Test
    @DisplayName("AUTH-30 : une authentification qui ne vient pas d'OAuth est refusée")
    void auth30NonOAuthAuthenticationRejected() throws Exception {
        MockHttpServletResponse response =
                handle(UsernamePasswordAuthenticationToken.authenticated("x", null, List.of()));

        assertThat(response.getRedirectedUrl()).isEqualTo(ClientRoutesController.LOGIN_REQUEST_REJECTED);
        assertThat(rejections).containsExactly("null:invalid_user_info");
    }

    @Test
    @DisplayName("AUTH-30 : un fournisseur inconnu est refusé")
    void auth30UnknownProviderRejected() throws Exception {
        MockHttpServletResponse response = handle(token("okta", Map.of("id", 1)));

        assertThat(response.getRedirectedUrl()).isEqualTo(ClientRoutesController.LOGIN_REQUEST_REJECTED);
        assertThat(rejections).containsExactly("null:invalid_user_info");
    }

    @Test
    @DisplayName("AUTH-31 : une identité illisible est refusée au nom de son fournisseur")
    void auth31UnreadableIdentityRejected() throws Exception {
        MockHttpServletResponse response = handle(token("github", Map.of("id", 7, "login", "octo")));

        assertThat(response.getRedirectedUrl()).isEqualTo(ClientRoutesController.LOGIN_REQUEST_REJECTED);
        assertThat(rejections).containsExactly("GITHUB:invalid_user_info");
    }

    @Test
    @DisplayName("ACC-07 : un compte lié qui ne peut plus servir est refusé, sans session ouverte")
    void acc07UnusableAccountRejected() throws Exception {
        outcome = new OAuthLoginOutcome.Rejected();

        MockHttpServletResponse response = handle(token(
                "github",
                Map.of(
                        "id",
                        7,
                        "login",
                        "octo",
                        GitHubUserService.PRIMARY_EMAIL,
                        "octo@exemple.ca",
                        GitHubUserService.PRIMARY_EMAIL_VERIFIED,
                        true)));

        assertThat(response.getRedirectedUrl()).isEqualTo(ClientRoutesController.LOGIN_REQUEST_REJECTED);
        assertThat(response.getCookies()).isEmpty();
    }
}
