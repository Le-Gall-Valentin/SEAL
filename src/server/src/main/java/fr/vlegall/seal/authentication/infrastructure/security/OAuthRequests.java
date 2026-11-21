package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SealPrincipal;
import fr.vlegall.seal.security.web.OAuthRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Ce que les gestionnaires du départ et du retour OAuth lisent de la requête, écrit une seule fois. */
final class OAuthRequests {

    /** Adresse du retour OAuth (AUTH-35). */
    private static final RequestMatcher CALLBACK =
            PathPatternRequestMatcher.withDefaults().matcher(OAuthRoutes.CALLBACK_BASE + "/{registrationId}");

    /** Contextes de sécurité tels que la session les garde (ADR 0007, point 2). */
    private static final HttpSessionSecurityContextRepository SESSIONS = new HttpSessionSecurityContextRepository();

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
    static Optional<UUID> fullSessionUser() {
        return fullSessionUser(
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication());
    }

    /**
     * Même lecture dans la session elle-même : les filtres OAuth remplacent le contexte de la requête par leur propre
     * authentification, et l'effacent après un échec.
     */
    static Optional<UUID> fullSessionUserInSession(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            return Optional.empty();
        }
        return fullSessionUser(SESSIONS.loadDeferredContext(request).get().getAuthentication());
    }

    private static Optional<UUID> fullSessionUser(@Nullable Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SealPrincipal principal)
                || authentication.getAuthorities().stream()
                        .noneMatch(a -> SealAuthorities.ACCOUNT_ACTIVE.equals(a.getAuthority()))) {
            return Optional.empty();
        }
        return Optional.of(principal.userId().value());
    }

    /** Le {@code registrationId} de l'adresse de retour, s'il s'agit d'un retour OAuth. */
    static @Nullable String callbackRegistrationId(HttpServletRequest request) {
        RequestMatcher.MatchResult match = CALLBACK.matcher(request);
        return match.isMatch() ? match.getVariables().get("registrationId") : null;
    }
}
