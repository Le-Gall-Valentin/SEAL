package fr.vlegall.seal.friends.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Section 14.4 : invitation en attente vue par l'un de ses deux comptes, avec le profil de l'autre. */
public record Invitation(UUID id, Person other, Instant createdAt) {

    public Invitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
