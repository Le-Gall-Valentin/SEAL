package fr.vlegall.archfixture.beta.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;

/** Contre-exemple de la règle 16 : écrire le contexte dans la session par une référence de méthode. */
public final class ContextWriterByReference {

    /** Écriture d'un contexte, telle que la référence de méthode la livre. */
    @FunctionalInterface
    public interface Store {
        void save(SecurityContext context, HttpServletRequest request, HttpServletResponse response);
    }

    private ContextWriterByReference() {}

    public static Store writer(SecurityContextRepository contexts) {
        return contexts::saveContext;
    }
}
