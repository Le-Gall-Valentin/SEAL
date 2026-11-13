package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lien de réinitialisation encore valable : ni consommé, ni invalidé, ni expiré (AUTH-70). */
public record LiveReset(UUID id, UserId user, Instant expiresAt) {

    public LiveReset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
