package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
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
     * Un code d'erreur OAuth (RFC 6749, section 5.2) : le paramètre {@code error} d'un retour vient du navigateur, donc
     * de n'importe qui. Tout autre texte est journalisé sous un nom fixe (SEC-21, A05).
     */
    private static final Pattern ERROR_CODE = Pattern.compile("[a-z_]{1,64}");

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
        login.rejected(provider(request), cause(exception));
        response.sendRedirect(REJECTED);
    }

    private @Nullable OAuthProvider provider(HttpServletRequest request) {
        RequestMatcher.MatchResult match = callback.matcher(request);
        String registrationId = match.getVariables().get("registrationId");
        return match.isMatch() && registrationId != null
                ? OAuthProvider.fromRegistrationId(registrationId).orElse(null)
                : null;
    }

    private static String cause(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError().getErrorCode();
            if (code != null && ERROR_CODE.matcher(code).matches()) {
                return code;
            }
        }
        return "oauth_error";
    }
}
