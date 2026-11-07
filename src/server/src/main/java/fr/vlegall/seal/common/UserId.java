package fr.vlegall.seal.common;

import java.util.Objects;
import java.util.UUID;

/** Identifiant d'un utilisateur, seul type partagé entre les contextes (ADR 0008). */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
