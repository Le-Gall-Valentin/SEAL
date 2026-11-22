package fr.vlegall.archfixture.alpha.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/** Contre-exemple de la règle 16 : un contexte écrit le contexte de sécurité dans la session, hors de security.session. */
public final class ContextWriter {

    private ContextWriter() {}

    public static void store(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
