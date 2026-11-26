package fr.vlegall.seal.security.ratelimit;

/**
 * Limite dont le seau appartient à un objet de courte vie, une connexion WebSocket (TRN-13) : il vit et meurt avec
 * lui, hors de la table partagée de {@link RateLimiter}, qu'un va-et-vient de connexions ne peut donc pas remplir.
 */
@FunctionalInterface
public interface LocalRateLimit {

    /** Prend un jeton, ou lève {@link RateLimitExceededException} avec le délai avant le prochain. */
    void consume();
}
