package fr.vlegall.seal.friends.infrastructure.web.dto;

import fr.vlegall.seal.friends.domain.model.Invitation;
import java.time.Instant;
import java.util.UUID;

/**
 * Invitation envoyée, et réponse 201 de l'invitation (ADR 0037, points 7 et 9) : le destinataire par son identifiant,
 * sans son nom affiché, qu'un non-ami n'obtient pas (API-01) ; date en ISO 8601 UTC.
 */
public record SentInvitationView(UUID id, UUID userId, String username, Instant createdAt) {

    public static SentInvitationView of(Invitation invitation) {
        return new SentInvitationView(
                invitation.id(),
                invitation.other().user().value(),
                invitation.other().username(),
                invitation.createdAt());
    }
}
