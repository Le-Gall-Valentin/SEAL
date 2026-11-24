package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Collection;

/**
 * API-02, FRD-21 : deux comptes sont-ils amis ? Demandé par les contextes {@code keys} (lecture des clés) et
 * {@code conversations} (directe, création et ajout, ADR 0039) (ADR 0008, règle 7) ; la signature ne porte que
 * {@link UserId}.
 */
public interface FriendshipQuery {

    /** Faux pour deux fois le même compte. */
    boolean areFriends(UserId a, UserId b);

    /**
     * FRD-21, GRP-02, GRP-03 : vrai si chacun de {@code others} est ami de {@code user}, en une requête ; faux si la
     * liste contient {@code user}. Demandé par le contexte {@code conversations}.
     */
    boolean areAllFriends(UserId user, Collection<UserId> others);
}
