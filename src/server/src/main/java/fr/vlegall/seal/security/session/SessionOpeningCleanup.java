package fr.vlegall.seal.security.session;

import jakarta.servlet.http.HttpServletRequest;

/**
 * AUTH-23, ADR 0032 (point 7) : ce qu'une session doit oublier quand un compte s'y ouvre. {@link SessionEstablisher},
 * transverse, appelle chaque nettoyage sans connaître les contextes qui les déclarent (ADR 0008).
 */
@FunctionalInterface
public interface SessionOpeningCleanup {

    /** Retire de la session de la requête ce qui ne doit pas survivre à l'ouverture d'un compte. */
    void clean(HttpServletRequest request);
}
