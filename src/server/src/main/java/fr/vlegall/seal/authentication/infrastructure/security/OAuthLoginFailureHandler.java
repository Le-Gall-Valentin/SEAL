package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Échec du retour OAuth : consentement refusé, {@code state} inconnu, fournisseur en erreur, jeton invalide. Le retour
 * est une navigation : il renvoie vers {@code /connexion} avec le seul code {@code REQUEST_REJECTED}, sans détail
 * (ADR 0032, point 2). Le journal note le fournisseur et le code d'erreur OAuth (SEC-20).
 */
final class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    /**
     * Retour vers l'écran de connexion (ADR 0024, point 5) avec un code de la section 14.6 du CDC. Les adresses de retour
     * sont des constantes : aucune ne dépend de la requête.
     */
    static final String REJECTED = "/connexion?erreur=REQUEST_REJECTED";

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
     * Codes que Spring Security lève lui-même, ou que rend le point {@code /token} du fournisseur, appelé par le serveur
     * (RFC 6749, section 5.2) : ils ne viennent pas du navigateur.
     */
    private static final Set<String> SERVER_ERRORS = Set.of(
            "invalid_client",
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
        response.sendRedirect(REJECTED);
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
        // Un code qui recopie le paramètre error vient du navigateur : seul un code de fournisseur y est cru.
        Set<String> known = code.equals(request.getParameter("error")) ? PROVIDER_ERRORS : SERVER_ERRORS;
        return known.contains(code) ? code : "oauth_error";
    }
}
