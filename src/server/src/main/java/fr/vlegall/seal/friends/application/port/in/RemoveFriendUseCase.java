package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.UserId;

/** FRD-11 : retirer un ami supprime l'amitié pour les deux. */
public interface RemoveFriendUseCase {

    /** Faux si {@code friend} n'est pas un ami de {@code user} (A01). */
    boolean unfriend(UserId user, UserId friend);
}
