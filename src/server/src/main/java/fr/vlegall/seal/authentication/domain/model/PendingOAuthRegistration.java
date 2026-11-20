package fr.vlegall.seal.authentication.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * AUTH-32 : inscription OAuth en attente du choix de l'identifiant, dans une session non authentifiée.
 *
 * @param authenticatedAt retour du fournisseur : il date le facteur de la session ouverte au terme de l'inscription
 *     (ADR 0032, point 8) et fait partir les {@value #LIFETIME_MINUTES} minutes de l'attente
 */
public record PendingOAuthRegistration(ExternalIdentity identity, Instant authenticatedAt) {

    /** AUTH-32 : 10 minutes. */
    public static final int LIFETIME_MINUTES = 10;

    public static final Duration LIFETIME = Duration.ofMinutes(LIFETIME_MINUTES);

    public PendingOAuthRegistration {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
    }

    /** Fin de l'attente : à cet instant, elle ne sert plus. */
    public Instant expiresAt() {
        return authenticatedAt.plus(LIFETIME);
    }

    /** Vrai tant que l'attente n'a pas expiré. */
    public boolean isAlive(Instant now) {
        return now.isBefore(expiresAt());
    }
}
