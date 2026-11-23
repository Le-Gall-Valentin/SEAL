package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.UserId;

/**
 * API-02, FRD-21 : deux comptes sont-ils amis ? Demandé par le contexte {@code keys} (ADR 0008, règle 7) ; la signature
 * ne porte que {@link UserId}.
 */
public interface FriendshipQuery {

    /** Faux pour deux fois le même compte. */
    boolean areFriends(UserId a, UserId b);
}
