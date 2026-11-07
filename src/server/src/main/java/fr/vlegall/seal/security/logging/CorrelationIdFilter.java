package fr.vlegall.seal.security.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-21 : un identifiant de corrélation relie les lignes de journal d'une même requête. Il vit dans le MDC le temps
 * de la requête et apparaît dans chaque ligne ({@code logging.pattern.correlation}).
 */
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";

    /** Attribut de requête qui garde l'identifiant pour le dispatch d'erreur de la même requête (ADR 0013, point 9). */
    static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getAttribute(REQUEST_ATTRIBUTE) instanceof String existing
                ? existing
                : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Le dispatch d'erreur reprend l'identifiant de la requête : ses lignes de journal restent reliées. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
