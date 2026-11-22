package fr.vlegall.archfixture.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;

/** Témoin permis de la règle 16 : security.session écrit le contexte dans la session. */
public final class FixtureSessionWriter {

    private FixtureSessionWriter() {}

    public static void store(
            SecurityContextRepository contexts,
            SecurityContext context,
            HttpServletRequest request,
            HttpServletResponse response) {
        contexts.saveContext(context, request, response);
    }
}
