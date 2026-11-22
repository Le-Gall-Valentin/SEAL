package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.application.port.in.OAuthReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.FullSessions;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientRoutesController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Retour OAuth réussi chez le fournisseur (AUTH-30 à AUTH-33). L'authentification de Spring n'est jamais gardée : le
 * compte lié reçoit une session SEAL, le premier passage une attente non authentifiée, la ré-authentification d'une
 * session complète un facteur redaté (AUTH-55, ADR 0034), et tout autre cas un refus. Les
 * adresses de retour sont des constantes (ADR 0024, point 5 ; ADR 0032, points 2 et 4).
 */
final class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String HOME = "/";

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final OAuthLoginUseCase login;
    private final OAuthReauthenticateUseCase reauthentication;
    private final SessionEstablisher sessions;
    private final PendingOAuthSession pending;

    OAuthLoginSuccessHandler(
            OAuthLoginUseCase login,
            OAuthReauthenticateUseCase reauthentication,
            SessionEstablisher sessions,
            PendingOAuthSession pending) {
        this.login = login;
        this.reauthentication = reauthentication;
        this.sessions = sessions;
        this.pending = pending;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        // L'authentification de Spring ne survit pas à ce gestionnaire, quelle que soit l'issue.
        holder.clearContext();
        boolean reauthenticating = request.getAttribute(OAuthAuthorizationRequests.REAUTHENTICATED_USER) != null;
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            reject(response, null, reauthenticating);
            return;
        }
        Optional<OAuthProvider> provider = OAuthProvider.fromRegistrationId(token.getAuthorizedClientRegistrationId());
        if (provider.isEmpty()) {
            reject(response, null, reauthenticating);
            return;
        }
        OAuth2User user = token.getPrincipal();
        if (user == null) {
            reject(response, provider.get(), reauthenticating);
            return;
        }
        ExternalIdentity identity;
        try {
            identity = ExternalIdentities.from(provider.get(), user);
        } catch (OAuth2AuthenticationException e) {
            reject(response, provider.get(), reauthenticating);
            return;
        }
        if (reauthenticating) {
            reauthenticate(request, response, identity);
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

    /**
     * AUTH-55, ADR 0034 (point 2) : la session complète qui a demandé la ré-authentification, relue dans la session
     * puisque Spring a remplacé le contexte de la requête, ne gagne le facteur redaté que pour l'identité déjà liée à
     * son compte. Un refus la laisse telle quelle. Le dépôt des requêtes d'autorisation a déjà vérifié que c'est bien
     * elle.
     */
    private void reauthenticate(HttpServletRequest request, HttpServletResponse response, ExternalIdentity identity)
            throws IOException {
        SecurityContext stored = FullSessions.stored(request);
        Optional<UserId> user = FullSessions.user(stored.getAuthentication());
        if (user.isEmpty() || !reauthentication.reauthenticate(user.get(), identity)) {
            response.sendRedirect(ClientRoutesController.rejectedTo(true));
            return;
        }
        holder.setContext(stored);
        sessions.refreshFactor(SealAuthorities.FACTOR_AUTHORIZATION_CODE, request, response);
        response.sendRedirect(ClientRoutesController.SETTINGS);
    }

    private void reject(HttpServletResponse response, @Nullable OAuthProvider provider, boolean reauthenticating)
            throws IOException {
        login.rejected(provider, "invalid_user_info");
        response.sendRedirect(ClientRoutesController.rejectedTo(reauthenticating));
    }
}
