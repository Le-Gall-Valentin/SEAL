package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.ListFriendsQuery;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.FriendSummary;
import fr.vlegall.seal.friends.domain.model.InteractionStats;
import fr.vlegall.seal.friends.domain.model.Page;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import fr.vlegall.seal.friends.domain.port.out.InteractionStatsPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ListFriendsHandler implements ListFriendsQuery {

    private final FriendshipRepository friendships;
    private final DirectoryPort directory;
    private final InteractionStatsPort interactions;

    public ListFriendsHandler(
            FriendshipRepository friendships, DirectoryPort directory, InteractionStatsPort interactions) {
        this.friendships = friendships;
        this.directory = directory;
        this.interactions = interactions;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendSummary> list(UserId user, Optional<UserId> after, int limit) {
        Page<Friend> raw = Page.cut(
                friendships.of(user, after, limit + 1),
                limit,
                friend -> friend.user().value());
        List<UserId> ids = raw.items().stream().map(Friend::user).toList();
        Map<UserId, Person> active =
                directory.activeProfiles(ids).stream().collect(Collectors.toMap(Person::user, Function.identity()));
        Map<UserId, InteractionStats> stats = interactions.between(user, active.keySet());
        return raw.withItems(raw.items().stream()
                .filter(friend -> active.containsKey(friend.user()))
                .map(friend -> new FriendSummary(
                        active.get(friend.user()),
                        friend.since(),
                        stats.getOrDefault(friend.user(), InteractionStats.NONE)))
                .toList());
    }
}
