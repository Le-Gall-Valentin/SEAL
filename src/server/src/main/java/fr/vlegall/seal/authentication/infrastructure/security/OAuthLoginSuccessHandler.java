package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientRoutesController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Retour OAuth réussi chez le fournisseur (AUTH-30 à AUTH-33). L'authentification de Spring n'est jamais gardée : le
 * compte lié reçoit une session SEAL, le premier passage une attente non authentifiée, et tout autre cas un refus. Les
 * adresses de retour sont des constantes (ADR 0024, point 5 ; ADR 0032, points 2 et 4).
 */
final class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String HOME = "/";

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final OAuthLoginUseCase login;
    private final SessionEstablisher sessions;
    private final PendingOAuthSession pending;

    OAuthLoginSuccessHandler(OAuthLoginUseCase login, SessionEstablisher sessions, PendingOAuthSession pending) {
        this.login = login;
        this.sessions = sessions;
        this.pending = pending;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        // L'authentification de Spring ne survit pas à ce gestionnaire, quelle que soit l'issue.
        holder.clearContext();
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            reject(response, null);
            return;
        }
        Optional<OAuthProvider> provider = OAuthProvider.fromRegistrationId(token.getAuthorizedClientRegistrationId());
        if (provider.isEmpty()) {
            reject(response, null);
            return;
        }
        OAuth2User user = token.getPrincipal();
        if (user == null) {
            reject(response, provider.get());
            return;
        }
        ExternalIdentity identity;
        try {
            identity = ExternalIdentities.from(provider.get(), user);
        } catch (OAuth2AuthenticationException e) {
            reject(response, provider.get());
            return;
        }
        switch (login.resolve(identity)) {
            case OAuthLoginOutcome.SignedIn signedIn -> {
                sessions.openWithAuthorizationCode(signedIn.user(), signedIn.active(), request, response);
                if (signedIn.active()) {
                    response.sendRedirect(HOME);
                } else {
                    response.sendRedirect(ClientRoutesController.VERIFY_EMAIL);
                }
            }
            case OAuthLoginOutcome.EmailInUse emailInUse ->
                response.sendRedirect(ClientRoutesController.LOGIN_OAUTH_EMAIL_IN_USE);
            case OAuthLoginOutcome.Unlinked unlinked -> {
                sessions.renew(request, response);
                pending.remember(request, unlinked.identity());
                response.sendRedirect(ClientRoutesController.CHOOSE_USERNAME);
            }
            case OAuthLoginOutcome.Rejected rejected ->
                response.sendRedirect(ClientRoutesController.LOGIN_REQUEST_REJECTED);
        }
    }

    private void reject(HttpServletResponse response, @Nullable OAuthProvider provider) throws IOException {
        login.rejected(provider, "invalid_user_info");
        response.sendRedirect(ClientRoutesController.LOGIN_REQUEST_REJECTED);
    }
}
