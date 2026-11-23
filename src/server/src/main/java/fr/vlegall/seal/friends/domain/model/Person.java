package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;

/** Profil public d'un compte actif, tel que {@code identity} le livre : ni courriel ni statut (ADR 0037, point 8). */
public record Person(UserId user, String username, String displayName) {

    public Person {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
    }
}
