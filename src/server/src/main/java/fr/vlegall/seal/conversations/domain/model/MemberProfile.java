package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;

/** Profil public d'un membre actif, tel que {@code identity} le livre : ni courriel ni statut (ADR 0039, point 6). */
public record MemberProfile(UserId user, String username, String displayName) {

    public MemberProfile {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
    }
}
