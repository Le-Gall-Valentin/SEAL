package fr.vlegall.seal.friends.infrastructure.web.dto;

import fr.vlegall.seal.friends.domain.model.Invitation;
import java.time.Instant;
import java.util.UUID;

/**
 * Invitation reçue (ADR 0037, points 7 et 9) : l'émetteur, qui a dévoilé son profil en invitant, avec son nom affiché ;
 * date en ISO 8601 UTC.
 */
public record ReceivedInvitationView(UUID id, UUID userId, String username, String displayName, Instant createdAt) {

    public static ReceivedInvitationView of(Invitation invitation) {
        return new ReceivedInvitationView(
                invitation.id(),
                invitation.other().user().value(),
                invitation.other().username(),
                invitation.other().displayName(),
                invitation.createdAt());
    }
}
