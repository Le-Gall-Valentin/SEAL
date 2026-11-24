package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.Collection;

/** FRD-21 : amitié revérifiée par le serveur à chaque création et à chaque ajout (contexte {@code friends}). */
public interface FriendshipPort {

    /** GRP-02, GRP-03 : chacun de {@code others} est ami de {@code user}, en une requête ; faux si {@code user} y est. */
    boolean areAllFriends(UserId user, Collection<UserId> others);
}
