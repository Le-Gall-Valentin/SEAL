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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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
     * Codes d'erreur journalisables tels quels : ceux de la RFC 6749 (sections 4.1.2.1 et 5.2) et ceux de Spring
     * Security. Le paramètre {@code error} d'un retour vient du navigateur, donc de n'importe qui : tout autre texte,
     * comme une cause propre à SEAL ({@code email_in_use}), est journalisé {@code oauth_error} (SEC-21, A09).
     */
    private static final Set<String> KNOWN_ERRORS = Set.of(
            "invalid_request",
            "unauthorized_client",
            "access_denied",
            "unsupported_response_type",
            "invalid_scope",
            "server_error",
            "temporarily_unavailable",
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

    private final RequestMatcher callback =
            PathPatternRequestMatcher.withDefaults().matcher("/login/oauth2/code/{registrationId}");
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
        RequestMatcher.MatchResult match = callback.matcher(request);
        String registrationId = match.getVariables().get("registrationId");
        return match.isMatch() && registrationId != null
                ? OAuthProvider.fromRegistrationId(registrationId).orElse(null)
                : null;
    }

    private static String cause(HttpServletRequest request, AuthenticationException exception) {
        if (request.getAttribute(OAuthAuthorizationRequests.REJECTION) instanceof String rejection) {
            return rejection;
        }
        if (exception instanceof OAuth2AuthenticationException oauth
                && KNOWN_ERRORS.contains(oauth.getError().getErrorCode())) {
            return oauth.getError().getErrorCode();
        }
        return "oauth_error";
    }
}
