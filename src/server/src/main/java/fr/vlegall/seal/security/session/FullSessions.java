package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Lecture d'une session complète (AUTH-16, ADR 0007 point 2), écrite une seule fois : le mode sudo, la
 * ré-authentification OAuth et ses retours en dépendent (ADR 0034). Rien n'est écrit ici : seul
 * {@link SessionEstablisher} écrit le contexte dans la session (règle ArchUnit 16).
 */
public final class FullSessions {

    private static final HttpSessionSecurityContextRepository CONTEXTS = new HttpSessionSecurityContextRepository();

    private FullSessions() {}

    /** Le compte d'une authentification de session complète ; vide pour l'anonyme et la session restreinte. */
    public static Optional<UserId> user(@Nullable Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SealPrincipal principal)
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> SealAuthorities.ACCOUNT_ACTIVE.equals(authority.getAuthority()))) {
            return Optional.empty();
        }
        return Optional.of(principal.userId());
    }

    /**
     * Le contexte de sécurité tel que la session le garde, vide sans session. Les filtres OAuth remplacent le contexte
     * de la requête par leur propre authentification, et l'effacent après un échec : la session, elle, garde le sien.
     */
    public static SecurityContext stored(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            return SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        }
        return CONTEXTS.loadDeferredContext(request).get();
    }

    /** Le compte de la session complète gardée par la session de la requête ; vide sinon. */
    public static Optional<UserId> storedUser(HttpServletRequest request) {
        return user(stored(request).getAuthentication());
    }
}
