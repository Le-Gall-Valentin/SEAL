package fr.vlegall.seal.security.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import java.util.OptionalLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AUTH-70, ADR 0023 : une session ouverte par mot de passe porte la version de ce mot de passe. Si la version de la base
 * a changé depuis, ou si le compte n'a plus de mot de passe, la session est invalidée et la requête continue sans
 * session : une route protégée répond 401, comme pour une session expirée. Ce filtre suit le chargement du contexte de
 * sécurité ; il couvre aussi une session enregistrée après la révocation, ce que la révocation seule ne peut pas.
 *
 * <p>Créé par {@code SecurityConfig}, jamais déclaré comme composant : Spring Boot l'enregistrerait une seconde fois
 * comme filtre de servlet.
 */
public final class CredentialVersionFilter extends OncePerRequestFilter {

    private final CredentialVersions versions;

    public CredentialVersionFilter(CredentialVersions versions) {
        this.versions = Objects.requireNonNull(versions, "versions");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof SealPrincipal principal
                && principal.credentialVersion() != null
                && isStale(principal, versions.current(principal.userId()))) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private static boolean isStale(SealPrincipal principal, OptionalLong current) {
        return current.isEmpty() || current.getAsLong() != Objects.requireNonNull(principal.credentialVersion());
    }
}
