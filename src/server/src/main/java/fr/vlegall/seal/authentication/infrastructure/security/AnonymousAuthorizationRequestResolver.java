package fr.vlegall.seal.authentication.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Départ OAuth ({@code GET /oauth2/authorization/{registrationId}}), réservé aux anonymes et à la seule méthode GET : un
 * {@code HEAD} ou un {@code POST} n'est pas résolu et ne crée donc aucune session (A06). Le filtre de Spring passe
 * avant les règles d'accès : ce résolveur ne rend rien pour une session ouverte ou pour un fournisseur non configuré,
 * et la requête suit alors son cours jusqu'au refus (403) ou à l'absence de route (404), au lieu de l'erreur 500 de
 * Spring (OPS-03). Le reste (state, nonce, PKCE) est celui de Spring.
 */
final class AnonymousAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String AUTHORIZATION_BASE = "/oauth2/authorization";

    private final PathPatternRequestMatcher start =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, AUTHORIZATION_BASE + "/{registrationId}");
    private final ClientRegistrationRepository registrations;
    private final DefaultOAuth2AuthorizationRequestResolver resolver;

    AnonymousAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
        this.resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, AUTHORIZATION_BASE);
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        RequestMatcher.MatchResult match = start.matcher(request);
        if (!match.isMatch()) {
            return null;
        }
        return resolve(request, match.getVariables().get("registrationId"));
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(
            HttpServletRequest request, @Nullable String clientRegistrationId) {
        if (clientRegistrationId == null
                || registrations.findByRegistrationId(clientRegistrationId) == null
                || OAuthRequests.signedIn()) {
            return null;
        }
        return resolver.resolve(request, clientRegistrationId);
    }
}
