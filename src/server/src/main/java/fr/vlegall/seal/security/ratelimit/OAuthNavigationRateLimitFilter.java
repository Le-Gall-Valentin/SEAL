package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.web.ClientAddress;
import fr.vlegall.seal.security.web.ClientRoutesController;
import fr.vlegall.seal.security.web.OAuthRoutes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-10, ADR 0032 (point 3) : départs et retours OAuth limités par IP, avant les filtres de Spring. Un départ refusé ne
 * crée donc aucune session, un retour refusé n'écrit rien au journal. Ce sont des routes de Spring Security, pas des
 * contrôleurs : {@code @RateLimited} ne les atteint pas. Toute méthode compte. Ce sont aussi des navigations : un refus
 * renvoie vers l'écran de connexion avec {@code RATE_LIMITED}, et {@code Retry-After}, plutôt qu'un 429 en JSON
 * qu'aucun écran n'afficherait.
 *
 * <p>Avant de compter, le filtre écarte ce qu'aucune navigation de SEAL n'émet, mais qu'un site tiers peut émettre en
 * masse depuis le navigateur de la victime pour épuiser ses limites (Fetch Metadata) : une sous-requête, un départ venu
 * d'une autre origine, et un préchargement ou un prérendu. Une requête sans ces en-têtes (navigateur ancien, outil)
 * reste comptée.
 */
public final class OAuthNavigationRateLimitFilter extends OncePerRequestFilter {

    private final RequestMatcher start =
            PathPatternRequestMatcher.withDefaults().matcher(OAuthRoutes.AUTHORIZATION_BASE + "/*");
    private final RequestMatcher callback =
            PathPatternRequestMatcher.withDefaults().matcher(OAuthRoutes.CALLBACK_BASE + "/*");
    private final RateLimiter limiter;

    public OAuthNavigationRateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limit(request) == null;
    }

    /** Contextes de sécurité tels que la session les garde (ADR 0007, point 2). */
    private static final HttpSessionSecurityContextRepository SESSIONS = new HttpSessionSecurityContextRepository();

    /** Session complète : son départ ou son retour est une ré-authentification (AUTH-55, ADR 0034). */
    private static boolean fullSession(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            return false;
        }
        Authentication authentication =
                SESSIONS.loadDeferredContext(request).get().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> SealAuthorities.ACCOUNT_ACTIVE.equals(a.getAuthority()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimit limit = limit(request);
        if (limit != null) {
            if (isSpeculative(request)) {
                // 403 sans corps : le navigateur abandonne le préchargement et refait la vraie navigation au clic ; une
                // redirection serait servie à l'activation d'un prérendu.
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            boolean signedIn = fullSession(request);
            if (!expectedNavigation(request)) {
                response.sendRedirect(
                        signedIn
                                ? ClientRoutesController.SETTINGS_REQUEST_REJECTED
                                : ClientRoutesController.LOGIN_REQUEST_REJECTED);
                return;
            }
            try {
                limiter.consume(limit, ClientAddress.key(request.getRemoteAddr()));
            } catch (RateLimitExceededException e) {
                e.retryAfterSeconds()
                        .ifPresent(seconds -> response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds)));
                // ADR 0034 : une ré-authentification revient aux paramètres, où sa session est restée ouverte.
                response.sendRedirect(
                        signedIn
                                ? ClientRoutesController.SETTINGS_RATE_LIMITED
                                : ClientRoutesController.LOGIN_RATE_LIMITED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * ADR 0032 (point 3) : une navigation de premier niveau ({@code Sec-Fetch-Dest: document},
     * {@code Sec-Fetch-Mode: navigate}) ; pour un départ, venue de SEAL même ou saisie ({@code Sec-Fetch-Site}
     * {@code same-origin} ou {@code none}, TRN-02). Le retour arrive du fournisseur, donc d'un autre site.
     */
    private boolean expectedNavigation(HttpServletRequest request) {
        if (isPresentAndNot(request, "Sec-Fetch-Dest", "document")
                || isPresentAndNot(request, "Sec-Fetch-Mode", "navigate")) {
            return false;
        }
        String site = request.getHeader("Sec-Fetch-Site");
        return !start.matches(request) || site == null || "same-origin".equals(site) || "none".equals(site);
    }

    /** ADR 0032 (point 3) : préchargement ou prérendu, que le navigateur annonce par {@code Sec-Purpose}. */
    private static boolean isSpeculative(HttpServletRequest request) {
        return request.getHeader("Sec-Purpose") != null;
    }

    private static boolean isPresentAndNot(HttpServletRequest request, String header, String expected) {
        String value = request.getHeader(header);
        return value != null && !expected.equals(value);
    }

    private @Nullable RateLimit limit(HttpServletRequest request) {
        if (start.matches(request)) {
            return RateLimit.OAUTH_STARTS_PER_IP;
        }
        return callback.matches(request) ? RateLimit.OAUTH_CALLBACKS_PER_IP : null;
    }
}
