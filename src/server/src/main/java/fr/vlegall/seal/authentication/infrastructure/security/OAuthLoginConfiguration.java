package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.common.OAuthClientProperties;
import fr.vlegall.seal.common.SealProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

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

    @Bean
    Customizer<OAuth2LoginConfigurer<HttpSecurity>> oauthLogin(OAuthClientRegistrations registrations) {
        OAuthLoginFailureHandler failure = new OAuthLoginFailureHandler();
        return login -> login.clientRegistrationRepository(registrations)
                .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                .authorizationEndpoint(endpoint ->
                        endpoint.authorizationRequestResolver(new AnonymousAuthorizationRequestResolver(registrations)))
                // Échec fermé tant que le retour n'ouvre aucune session SEAL (AUTH-31, AUTH-32).
                .successHandler(
                        (request, response, authentication) -> response.sendRedirect(OAuthLoginFailureHandler.REJECTED))
                .failureHandler(failure);
    }
}
