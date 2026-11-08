package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Ouvre et élève les sessions (ADR 0007, point 2). À chaque fois : nouvel identifiant de session contre la fixation
 * (AUTH-23), ancien jeton CSRF retiré (le client recharge le sien par {@code GET /api/v1/csrf}, AUTH-60), et contexte
 * enregistré explicitement dans la session (AUTH-20).
 */
@Component
public final class SessionEstablisher {

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy renewal;
    private final Clock clock;

    public SessionEstablisher(CsrfTokenRepository csrfTokens, Clock clock) {
        this.renewal = new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
        this.clock = clock;
    }

    /** AUTH-16 : session restreinte, après l'inscription ou la connexion d'un compte non vérifié. */
    public void openRestricted(UserId user, HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_PENDING_EMAIL),
                FactorGrantedAuthority.withAuthority(SealAuthorities.FACTOR_PASSWORD)
                        .issuedAt(clock.instant())
                        .build());
        establish(new SealPrincipal(user), authorities, request, response);
    }

    /**
     * AUTH-16 : la vérification réussie rend complète la session restreinte du même compte, qui garde ses facteurs et
     * leur date. Toute autre situation est une erreur de programmation : échec fermé.
     */
    public void upgradeToFull(UserId user, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = holder.getContext().getAuthentication();
        if (current == null
                || !(current.getPrincipal() instanceof SealPrincipal principal)
                || !principal.userId().equals(user)
                || current.getAuthorities().stream()
                        .noneMatch(a -> SealAuthorities.ACCOUNT_PENDING_EMAIL.equals(a.getAuthority()))) {
            throw new IllegalStateException("aucune session restreinte de ce compte à élever");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_ACTIVE));
        current.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .forEach(authorities::add);
        establish(principal, authorities, request, response);
    }

    /** Facteurs obtenus par la session, sans leur préfixe : {@code ["PASSWORD"]} au lot 1 ({@code GET /me}). */
    public List<String> factors(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(SealAuthorities.FACTOR_PREFIX))
                .map(authority -> authority.substring(SealAuthorities.FACTOR_PREFIX.length()))
                .sorted()
                .toList();
    }

    private void establish(
            SealPrincipal principal,
            List<GrantedAuthority> authorities,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        renewal.onAuthentication(authentication, request, response);
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
