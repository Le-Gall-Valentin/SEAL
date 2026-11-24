package fr.vlegall.seal.keys.infrastructure.friends;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.FriendshipQuery;
import fr.vlegall.seal.keys.domain.port.out.RelationPort;
import org.springframework.stereotype.Component;

/** API-02 : demande l'amitié au contexte {@code friends}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class FriendRelationAdapter implements RelationPort {

    private final FriendshipQuery friendships;

    public FriendRelationAdapter(FriendshipQuery friendships) {
        this.friendships = friendships;
    }

    @Override
    public boolean related(UserId caller, UserId owner) {
        return friendships.areFriends(caller, owner);
    }
}
