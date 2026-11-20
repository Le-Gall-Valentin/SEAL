package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.common.OAuthClientProperties;
import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.security.session.SessionEstablisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * Connexion OAuth (AUTH-30 à AUTH-33). {@code SecurityConfig}, transverse, ne connaît aucun contexte (ADR 0008) : il
 * applique ce réglage de {@code oauth2Login}, qu'il reçoit par son type Spring (ADR 0031).
 */
@Configuration(proxyBeanMethods = false)
class OAuthLoginConfiguration {

    /** OPS-03 : les applications de {@code seal.oauth}, aucune comprise. */
    @Bean
    OAuthClientRegistrations oauthClientRegistrations(OAuthClientProperties properties, SealProperties seal) {
        return OAuthClientRegistrations.from(properties, seal.origin());
    }

    /** AUTH-30 : jetons d'identité vérifiés comme le fait Spring, avec des JWKS lus en temps borné (A10). */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoders() {
        return new IdTokenDecoders();
    }

    @Bean
    Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauthLogin(
            OAuthClientRegistrations registrations,
            OAuthLoginUseCase login,
            SessionEstablisher sessions,
            PendingOAuthSession pending) {
        return configurer -> configurer
                .clientRegistrationRepository(registrations)
                .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                .authorizationEndpoint(endpoint ->
                        endpoint.authorizationRequestResolver(new AnonymousAuthorizationRequestResolver(registrations)))
                .tokenEndpoint(token -> token.accessTokenResponseClient(ProviderHttp.tokenClient()))
                .userInfoEndpoint(userInfo ->
                        userInfo.userService(new GitHubUserService()).oidcUserService(new GoogleUserService()))
                .successHandler(new OAuthLoginSuccessHandler(login, sessions, pending))
                .failureHandler(new OAuthLoginFailureHandler(login));
    }
}
