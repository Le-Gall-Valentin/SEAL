package fr.vlegall.seal.conversations.infrastructure.web.dto;

import fr.vlegall.seal.conversations.domain.model.MemberSummary;
import java.util.UUID;

/** Membre d'une conversation : profil public et rôle ({@code ADMIN}, {@code MEMBER}), ADR 0039 (point 6). */
public record MemberView(UUID userId, String username, String displayName, String role) {

    public static MemberView of(MemberSummary member) {
        return new MemberView(
                member.profile().user().value(),
                member.profile().username(),
                member.profile().displayName(),
                member.role().name());
    }
}
