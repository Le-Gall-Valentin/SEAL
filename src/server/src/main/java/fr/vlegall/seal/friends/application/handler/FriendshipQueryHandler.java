package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.FriendshipQuery;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import java.util.Collection;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class FriendshipQueryHandler implements FriendshipQuery {

    private final FriendshipRepository friendships;

    public FriendshipQueryHandler(FriendshipRepository friendships) {
        this.friendships = friendships;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(UserId a, UserId b) {
        return !a.equals(b) && friendships.exists(UserPair.of(a, b));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areAllFriends(UserId user, Collection<UserId> others) {
        Set<UserId> distinct = Set.copyOf(others);
        return !distinct.contains(user) && friendships.countAmong(user, distinct) == distinct.size();
    }
}
