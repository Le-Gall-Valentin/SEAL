package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.FullSessions;
import fr.vlegall.seal.security.web.OAuthRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Ce que les gestionnaires du départ et du retour OAuth lisent de la requête, écrit une seule fois. */
final class OAuthRequests {

    /** Adresse du retour OAuth (AUTH-35). */
    private static final RequestMatcher CALLBACK =
            PathPatternRequestMatcher.withDefaults().matcher(OAuthRoutes.CALLBACK_BASE + "/{registrationId}");

    private OAuthRequests() {}

    /** Vrai si la requête porte une session SEAL authentifiée, chargée avant les filtres OAuth. */
    static boolean signedIn() {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * AUTH-55, ADR 0034 : le compte de la session complète portée par la requête, chargée avant les filtres OAuth ; vide
     * pour l'anonyme et pour la session restreinte (AUTH-16).
     */
    static Optional<UserId> fullSessionUser() {
        return FullSessions.user(
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication());
    }

    /** Le {@code registrationId} de l'adresse de retour, s'il s'agit d'un retour OAuth. */
    static @Nullable String callbackRegistrationId(HttpServletRequest request) {
        RequestMatcher.MatchResult match = CALLBACK.matcher(request);
        return match.isMatch() ? match.getVariables().get("registrationId") : null;
    }
}
