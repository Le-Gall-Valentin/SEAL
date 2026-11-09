package fr.vlegall.seal.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Refus de la chaîne de filtres (401 sans session, 403 sans jeton CSRF ou sans droit, 413 pour un corps trop grand) rendus par le même gestionnaire
 * Problem Details que le reste de l'API (QA-05, ADR 0010) : les exceptions levées dans la chaîne de filtres sont
 * confiées au {@link HandlerExceptionResolver} de Spring MVC.
 */
@Component
public final class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailsSecurityHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        resolve(request, response, exception, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        resolve(request, response, exception, HttpServletResponse.SC_FORBIDDEN);
    }

    /** SEC-01 : corps trop grand, 413 {@code REQUEST_REJECTED} ({@link RequestBodyLimitFilter}). */
    public void rejectTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        resolve(request, response, new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE), 413);
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception exception, int fallback)
            throws IOException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            // Échec fermé : sans gestionnaire, le refus reste un refus, sans corps.
            response.sendError(fallback);
        }
    }
}
