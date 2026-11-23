package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;

/** Section 10.1 : le destinataire refuse, sans notification de refus. */
public interface DeclineFriendRequestUseCase {

    /** Faux si l'invitation est inconnue, d'un autre, déjà close, ou si l'autre compte n'est plus actif (A01). */
    boolean decline(UserId user, UUID requestId);
}
