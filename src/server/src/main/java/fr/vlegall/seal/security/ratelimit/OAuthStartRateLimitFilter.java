package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.security.web.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-10, ADR 0032 (point 3) : départs OAuth limités par IP, avant le filtre de Spring qui crée la session de la
 * requête d'autorisation. Un départ refusé ne crée donc aucune session. Le départ est une route de Spring Security, pas
 * un contrôleur : {@code @RateLimited} ne l'atteint pas.
 */
public final class OAuthStartRateLimitFilter extends OncePerRequestFilter {

    /** Refus en Problem Details, écrit par la chaîne de sécurité. */
    @FunctionalInterface
    public interface Rejection {
        void reject(HttpServletRequest request, HttpServletResponse response, SealException exception)
                throws IOException;
    }

    private final RequestMatcher start =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorization/*");
    private final RateLimiter limiter;
    private final Rejection rejection;

    public OAuthStartRateLimitFilter(RateLimiter limiter, Rejection rejection) {
        this.limiter = limiter;
        this.rejection = rejection;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !start.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            limiter.consume(RateLimit.OAUTH_STARTS_PER_IP, ClientAddress.key(request.getRemoteAddr()));
        } catch (RateLimitExceededException e) {
            rejection.reject(request, response, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
