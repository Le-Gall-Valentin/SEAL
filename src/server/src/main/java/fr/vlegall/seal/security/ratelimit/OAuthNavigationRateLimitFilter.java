package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.security.web.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
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
 * masse depuis le navigateur de la victime pour épuiser ses limites (Fetch Metadata) : une sous-requête, et un départ
 * venu d'une autre origine. Une requête sans ces en-têtes (navigateur ancien, outil) reste comptée.
 */
public final class OAuthNavigationRateLimitFilter extends OncePerRequestFilter {

    /** Retour vers l'écran de connexion (ADR 0024, point 5), avec un code de la section 14.6 du CDC. */
    static final String RATE_LIMITED = "/connexion?erreur=RATE_LIMITED";

    /** Retour vers l'écran de connexion pour une requête écartée par Fetch Metadata, sans détail. */
    static final String REQUEST_REJECTED = "/connexion?erreur=REQUEST_REJECTED";

    private final RequestMatcher start =
            PathPatternRequestMatcher.withDefaults().matcher("/oauth2/authorization/*");
    private final RequestMatcher callback =
            PathPatternRequestMatcher.withDefaults().matcher("/login/oauth2/code/*");
    private final RateLimiter limiter;

    public OAuthNavigationRateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limit(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimit limit = limit(request);
        if (limit != null) {
            if (!expectedNavigation(request)) {
                response.sendRedirect(REQUEST_REJECTED);
                return;
            }
            try {
                limiter.consume(limit, ClientAddress.key(request.getRemoteAddr()));
            } catch (RateLimitExceededException e) {
                e.retryAfterSeconds()
                        .ifPresent(seconds -> response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds)));
                response.sendRedirect(RATE_LIMITED);
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
