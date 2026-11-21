package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.security.web.ClientRoutesController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;

/**
 * Échec du retour OAuth : consentement refusé, {@code state} inconnu, fournisseur en erreur, jeton invalide. Le retour
 * est une navigation : il renvoie vers {@code /connexion}, ou vers {@code /parametres} pour une ré-authentification
 * (ADR 0034), avec le seul code {@code REQUEST_REJECTED}, sans détail (ADR 0032, point 2). Le journal note le fournisseur et le code d'erreur OAuth (SEC-20).
 */
final class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    /**
     * Codes qu'un fournisseur peut renvoyer dans le paramètre {@code error} du retour (RFC 6749, section 4.1.2.1). Ce
     * paramètre vient du navigateur, donc de n'importe qui : tout autre texte est journalisé {@code oauth_error}, pour
     * qu'aucun retour forgé n'imite une cause interne ({@code invalid_nonce}, {@code email_in_use}) (SEC-21, A09).
     */
    private static final Set<String> PROVIDER_ERRORS = Set.of(
            "invalid_request",
            "unauthorized_client",
            "access_denied",
            "unsupported_response_type",
            "invalid_scope",
            "server_error",
            "temporarily_unavailable");

    /**
     * Codes que Spring Security lève lui-même ({@code invalid_request} pour un retour sans code ni erreur), ou que rend
     * le point {@code /token} du fournisseur, appelé par le serveur (RFC 6749, section 5.2) : ils ne viennent pas du
     * navigateur.
     */
    private static final Set<String> SERVER_ERRORS = Set.of(
            "invalid_request",
            "invalid_client",
            "unauthorized_client",
            "invalid_scope",
            "invalid_grant",
            "unsupported_grant_type",
            "authorization_request_not_found",
            "client_registration_not_found",
            "invalid_state_parameter",
            "invalid_redirect_uri_parameter",
            "invalid_token_response",
            "invalid_user_info_response",
            "invalid_user_info",
            "invalid_id_token",
            "invalid_nonce",
            "missing_user_info_uri",
            "missing_user_name_attribute");

    private final OAuthLoginUseCase login;

    OAuthLoginFailureHandler(OAuthLoginUseCase login) {
        this.login = login;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        login.rejected(provider(request), cause(request, exception));
        // ADR 0034 : une ré-authentification refusée ramène aux paramètres, où la session complète est restée ouverte.
        // Un
        // retour de connexion refusé ramène à la connexion, même si une session s'est ouverte entre-temps (ADR 0032).
        boolean reauthenticating = request.getAttribute(OAuthAuthorizationRequests.REAUTHENTICATED_USER) != null;
        response.sendRedirect(
                reauthenticating
                        ? ClientRoutesController.SETTINGS_REQUEST_REJECTED
                        : ClientRoutesController.LOGIN_REQUEST_REJECTED);
    }

    /** Le fournisseur du départ s'il est connu, sinon celui de l'adresse de retour. */
    private @Nullable OAuthProvider provider(HttpServletRequest request) {
        if (request.getAttribute(OAuthAuthorizationRequests.STARTED_WITH) instanceof String startedWith) {
            return OAuthProvider.fromRegistrationId(startedWith).orElse(null);
        }
        String registrationId = OAuthRequests.callbackRegistrationId(request);
        return registrationId == null
                ? null
                : OAuthProvider.fromRegistrationId(registrationId).orElse(null);
    }

    private static String cause(HttpServletRequest request, AuthenticationException exception) {
        if (request.getAttribute(OAuthAuthorizationRequests.REJECTION) instanceof String rejection) {
            return rejection;
        }
        if (!(exception instanceof OAuth2AuthenticationException oauth)) {
            return "oauth_error";
        }
        String code = oauth.getError().getErrorCode();
        // Spring traite comme une réussite tout retour qui porte un code : l'erreur vient alors de l'échange, même si
        // le retour porte aussi error. Sans code, Spring recopie error, venu du navigateur : seul un code de
        // fournisseur y est cru.
        boolean fromBrowser = !StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CODE))
                && code.equals(request.getParameter(OAuth2ParameterNames.ERROR));
        Set<String> known = fromBrowser ? PROVIDER_ERRORS : SERVER_ERRORS;
        return known.contains(code) ? code : "oauth_error";
    }
}
