package fr.vlegall.seal.conversations.infrastructure.friends;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.port.out.FriendshipPort;
import fr.vlegall.seal.friends.application.port.in.FriendshipQuery;
import org.springframework.stereotype.Component;

/** FRD-21 : demande l'amitié au contexte {@code friends}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class FriendshipAdapter implements FriendshipPort {

    private final FriendshipQuery friendships;

    public FriendshipAdapter(FriendshipQuery friendships) {
        this.friendships = friendships;
    }

    @Override
    public boolean areFriends(UserId a, UserId b) {
        return friendships.areFriends(a, b);
    }
}
