package fr.vlegall.seal.security;

import fr.vlegall.seal.common.SealProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * TRN-20 (ADR 0014, point 1) : le pare-feu de Spring Security refuse certaines requêtes avant le filtre qui écrit les
 * en-têtes, et Tomcat refuse TRACE lui-même ; leur réponse passe par le dispatch d'erreur, où ce filtre pose les
 * en-têtes de la section 12.3 s'ils manquent, avec les valeurs de {@link SecurityConfig}.
 */
final class ErrorDispatchHeadersFilter implements Filter {

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    private final String contentSecurityPolicy;
    private final String strictTransportSecurity;

    ErrorDispatchHeadersFilter(SealProperties seal) {
        this.contentSecurityPolicy = SecurityConfig.contentSecurityPolicy(seal);
        this.strictTransportSecurity = "max-age=" + seal.security().hstsMaxAge().toSeconds();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http
                && response instanceof HttpServletResponse httpResponse
                && !httpResponse.containsHeader(CONTENT_SECURITY_POLICY)) {
            httpResponse.setHeader(CONTENT_SECURITY_POLICY, contentSecurityPolicy);
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("Referrer-Policy", "no-referrer");
            httpResponse.setHeader("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY);
            httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            httpResponse.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Expires", "0");
            if (http.isSecure()) {
                httpResponse.setHeader("Strict-Transport-Security", strictTransportSecurity);
            }
        }
        chain.doFilter(request, response);
    }
}
