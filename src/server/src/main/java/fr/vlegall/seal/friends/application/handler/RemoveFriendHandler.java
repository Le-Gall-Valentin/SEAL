package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.RemoveFriendUseCase;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class RemoveFriendHandler implements RemoveFriendUseCase {

    private final FriendshipRepository friendships;
    private final FriendsEventPort events;

    public RemoveFriendHandler(FriendshipRepository friendships, FriendsEventPort events) {
        this.friendships = friendships;
        this.events = events;
    }

    /** FRD-11 : la conversation directe et les groupes communs ne changent pas (lots 3 et 4). */
    @Override
    @Transactional
    public boolean unfriend(UserId user, UserId friend) {
        if (user.equals(friend) || !friendships.dissolve(UserPair.of(user, friend))) {
            return false;
        }
        events.friendRemoved(user, friend);
        return true;
    }
}
