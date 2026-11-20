package fr.vlegall.seal.authentication.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * AUTH-32 : inscription OAuth en attente, gardée dans la session non authentifiée jusqu'au choix de l'identifiant.
 *
 * @param expiresAt fin de l'attente, {@value #LIFETIME_MINUTES} minutes après le retour du fournisseur
 */
public record PendingOAuthRegistration(ExternalIdentity identity, Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** AUTH-32 : 10 minutes. */
    public static final int LIFETIME_MINUTES = 10;

    public static final Duration LIFETIME = Duration.ofMinutes(LIFETIME_MINUTES);

    public PendingOAuthRegistration {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** L'attente qui commence au retour du fournisseur. */
    public static PendingOAuthRegistration startingAt(ExternalIdentity identity, Instant now) {
        return new PendingOAuthRegistration(identity, now.plus(LIFETIME));
    }

    /** Vrai tant que l'attente n'a pas expiré : à {@code expiresAt}, elle ne sert plus. */
    public boolean isAlive(Instant now) {
        return now.isBefore(expiresAt);
    }
}
