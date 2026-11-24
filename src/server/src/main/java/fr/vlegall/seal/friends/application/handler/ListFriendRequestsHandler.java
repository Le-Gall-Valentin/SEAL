package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.ListFriendRequestsQuery;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.Invitation;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ListFriendRequestsHandler implements ListFriendRequestsQuery {

    private final FriendRequestRepository requests;
    private final DirectoryPort directory;

    public ListFriendRequestsHandler(FriendRequestRepository requests, DirectoryPort directory) {
        this.requests = requests;
        this.directory = directory;
    }

    /** SEC-03 : seules les invitations dont l'appelant est l'une des deux extrémités ; l'autre compte encore actif. */
    @Override
    @Transactional(readOnly = true)
    public Page<Invitation> list(UserId user, Direction direction, Optional<UUID> after, int limit) {
        Page<FriendRequest> raw =
                Page.cut(requests.pending(user, direction, after, limit + 1), limit, FriendRequest::id);
        Map<UserId, Person> active =
                directory
                        .activeProfiles(raw.items().stream()
                                .map(request -> request.other(user))
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Person::user, Function.identity()));
        return raw.withItems(raw.items().stream()
                .filter(request -> active.containsKey(request.other(user)))
                .map(request -> new Invitation(request.id(), active.get(request.other(user)), request.createdAt()))
                .toList());
    }
}
