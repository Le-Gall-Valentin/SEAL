package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.web.OAuthRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Départ OAuth ({@code GET /oauth2/authorization/{registrationId}}), à la seule méthode GET : un {@code HEAD} ou un
 * {@code POST} n'est pas résolu et ne crée donc aucune session (A06). Deux départs sont possibles :
 *
 * <ul>
 *   <li>d'une session anonyme, une connexion (AUTH-30) ;
 *   <li>d'une session complète, une ré-authentification (AUTH-55, ADR 0034, point 2) : le fournisseur reçoit
 *       {@code prompt=select_account}, qui force le choix du compte, et la requête d'autorisation garde le compte de la
 *       session, que le retour devra retrouver ({@link OAuthAuthorizationRequests}).
 * </ul>
 *
 * <p>Le filtre de Spring passe avant les règles d'accès : ce résolveur ne rend rien pour une session restreinte, pour
 * un fournisseur non configuré, ou, pour une session complète, vers un fournisseur que son compte n'a pas lié. La
 * requête suit alors son cours jusqu'au refus (403), au retour vers les paramètres ({@code OAuthStartController}) ou à
 * l'absence de route (404), au lieu de l'erreur 500 de Spring (OPS-03). Le reste (state, nonce, PKCE) est celui de
 * Spring.
 */
final class OAuthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** ADR 0034 : seule valeur de {@code prompt} qu'acceptent à la fois Google et GitHub pour forcer un choix. */
    static final String SELECT_ACCOUNT = "select_account";

    private final PathPatternRequestMatcher start = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.GET, OAuthRoutes.AUTHORIZATION_BASE + "/{registrationId}");
    private final ClientRegistrationRepository registrations;
    private final OAuthReauthenticateUseCase reauthenticated;
    private final DefaultOAuth2AuthorizationRequestResolver login;
    private final DefaultOAuth2AuthorizationRequestResolver reauthentication;

    OAuthAuthorizationRequestResolver(
            ClientRegistrationRepository registrations, OAuthReauthenticateUseCase reauthenticated) {
        this.registrations = registrations;
        this.reauthenticated = reauthenticated;
        this.login = new DefaultOAuth2AuthorizationRequestResolver(registrations, OAuthRoutes.AUTHORIZATION_BASE);
        this.reauthentication =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, OAuthRoutes.AUTHORIZATION_BASE);
        this.reauthentication.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(
                        parameters -> parameters.put("prompt", SELECT_ACCOUNT))
                .attributes(attributes -> OAuthRequests.fullSessionUser()
                        .ifPresent(user -> attributes.put(
                                OAuthAuthorizationRequests.REAUTHENTICATED_USER,
                                user.value().toString()))));
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
        if (clientRegistrationId == null || registrations.findByRegistrationId(clientRegistrationId) == null) {
            return null;
        }
        Optional<UserId> fullSession = OAuthRequests.fullSessionUser();
        if (fullSession.isPresent()) {
            // ADR 0034 : pas d'aller-retour vers un fournisseur que le compte n'a pas lié ; OAuthStartController
            // renvoie
            // alors aux paramètres.
            Optional<OAuthProvider> provider = OAuthProvider.fromRegistrationId(clientRegistrationId);
            if (provider.isEmpty() || !reauthenticated.canStart(fullSession.get(), provider.get())) {
                return null;
            }
            return reauthentication.resolve(request, clientRegistrationId);
        }
        if (OAuthRequests.signedIn()) {
            return null;
        }
        return login.resolve(request, clientRegistrationId);
    }
}
