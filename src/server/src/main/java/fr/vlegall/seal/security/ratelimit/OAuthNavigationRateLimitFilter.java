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
 */
public final class OAuthNavigationRateLimitFilter extends OncePerRequestFilter {

    /** Retour vers l'écran de connexion (ADR 0024, point 5), avec un code de la section 14.6 du CDC. */
    static final String RATE_LIMITED = "/connexion?erreur=RATE_LIMITED";

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

    private @Nullable RateLimit limit(HttpServletRequest request) {
        if (start.matches(request)) {
            return RateLimit.OAUTH_STARTS_PER_IP;
        }
        return callback.matches(request) ? RateLimit.OAUTH_CALLBACKS_PER_IP : null;
    }
}
