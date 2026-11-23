package fr.vlegall.seal.friends.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Section 10.1 : issue de {@code POST /api/v1/friend-requests}, une par ligne du tableau. */
public sealed interface InvitationOutcome {

    /** 201 : invitation créée. */
    record Sent(Invitation invitation) implements InvitationOutcome {
        public Sent {
            Objects.requireNonNull(invitation, "invitation");
        }
    }

    /** ADR 0037, point 6 : saisie hors du format d'AUTH-01. */
    record Malformed() implements InvitationOutcome {}

    /** FRD-02 : aucun compte actif ne porte cet identifiant. */
    record UserNotFound() implements InvitationOutcome {}

    record Self() implements InvitationOutcome {}

    /** FRD-03 : blocage dans un sens ou l'autre, ou invitations fermées ; la cause n'est pas dite. */
    record CannotInvite() implements InvitationOutcome {}

    record AlreadyFriends() implements InvitationOutcome {}

    /** FRD-01 : l'appelant a déjà une invitation en attente vers ce compte. */
    record AlreadyPending() implements InvitationOutcome {}

    /** FRD-01 : l'autre a déjà invité l'appelant. */
    record IncomingExists(UUID requestId) implements InvitationOutcome {
        public IncomingExists {
            Objects.requireNonNull(requestId, "requestId");
        }
    }
}
