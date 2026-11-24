package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** FRD-21 : amitié revérifiée par le serveur à chaque création et à chaque ajout (contexte {@code friends}). */
@FunctionalInterface
public interface FriendshipPort {

    /** Faux pour deux fois le même compte. */
    boolean areFriends(UserId a, UserId b);
}
