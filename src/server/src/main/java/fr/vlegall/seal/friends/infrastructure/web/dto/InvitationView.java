package fr.vlegall.seal.friends.infrastructure.web.dto;

import fr.vlegall.seal.friends.domain.model.Invitation;
import java.time.Instant;
import java.util.UUID;

/** Invitation servie par l'API (ADR 0037, point 7) : l'autre compte selon le sens, date en ISO 8601 UTC. */
public record InvitationView(UUID id, UUID userId, String username, String displayName, Instant createdAt) {

    public static InvitationView of(Invitation invitation) {
        return new InvitationView(
                invitation.id(),
                invitation.other().user().value(),
                invitation.other().username(),
                invitation.other().displayName(),
                invitation.createdAt());
    }
}
