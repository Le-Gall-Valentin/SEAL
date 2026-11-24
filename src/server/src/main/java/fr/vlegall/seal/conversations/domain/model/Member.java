package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Objects;

/** Membre actif d'une conversation, avec son rôle et son arrivée (ADR 0039, point 4). */
public record Member(UserId user, Role role, Instant joinedAt) {

    public Member {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    Member asAdmin() {
        return new Member(user, Role.ADMIN, joinedAt);
    }
}
