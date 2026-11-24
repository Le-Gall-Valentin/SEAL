package fr.vlegall.seal.conversations.domain.model;

import java.util.Objects;

/** Membre affiché d'une conversation : profil public et rôle (ADR 0039, point 6). */
public record MemberSummary(MemberProfile profile, Role role) {

    public MemberSummary {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(role, "role");
    }
}
