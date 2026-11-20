package fr.vlegall.seal.authentication.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Échec du retour OAuth : consentement refusé, {@code state} inconnu, fournisseur en erreur, jeton invalide. Le retour
 * est une navigation : il renvoie vers {@code /connexion} avec le seul code {@code REQUEST_REJECTED}, sans détail
 * (ADR 0032, point 2).
 */
final class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    /**
     * Retour vers l'écran de connexion (ADR 0024, point 5) avec un code de la section 14.6 du CDC. Les adresses de retour
     * sont des constantes : aucune ne dépend de la requête.
     */
    static final String REJECTED = "/connexion?erreur=REQUEST_REJECTED";

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.sendRedirect(REJECTED);
    }
}
