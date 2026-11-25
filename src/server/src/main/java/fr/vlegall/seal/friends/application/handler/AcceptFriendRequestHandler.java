package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.AcceptFriendRequestUseCase;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.port.out.FriendNotificationPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class AcceptFriendRequestHandler implements AcceptFriendRequestUseCase {

    private final OpenRequests open;
    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final FriendsEventPort events;
    private final FriendNotificationPort notifications;
    private final Clock clock;

    public AcceptFriendRequestHandler(
            OpenRequests open,
            FriendRequestRepository requests,
            FriendshipRepository friendships,
            FriendsEventPort events,
            FriendNotificationPort notifications,
            Clock clock) {
        this.open = open;
        this.requests = requests;
        this.friendships = friendships;
        this.events = events;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** FRD-01 : le verrou de l'invitation sérialise deux acceptations ; la seconde la trouve close. */
    @Override
    @Transactional
    public boolean accept(UserId receiver, UUID requestId) {
        Optional<FriendRequest> locked = open.lockAddressedTo(receiver, requestId);
        if (locked.isEmpty()) {
            return false;
        }
        FriendRequest request = locked.get();
        Instant now = clock.instant();
        requests.close(request.id(), RequestStatus.ACCEPTED, now);
        friendships.create(request.pair(), now);
        events.requestAccepted(receiver, request.sender(), request.id());
        notifications.relationChanged(receiver, request.sender());
        return true;
    }
}
