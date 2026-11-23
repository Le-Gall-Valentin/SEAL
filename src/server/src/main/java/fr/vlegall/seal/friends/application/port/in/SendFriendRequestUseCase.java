package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.InvitationOutcome;

/** FRD-01 à FRD-03, section 10.1 : inviter un compte par son identifiant. */
public interface SendFriendRequestUseCase {

    /** {@code input} est la saisie brute, normalisée ici (ADR 0037, point 6). */
    InvitationOutcome send(UserId sender, String input);
}
