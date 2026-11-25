package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.DeclineFriendRequestUseCase;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.port.out.FriendNotificationPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class DeclineFriendRequestHandler implements DeclineFriendRequestUseCase {

    private final OpenRequests open;
    private final FriendRequestRepository requests;
    private final FriendNotificationPort notifications;
    private final Clock clock;

    public DeclineFriendRequestHandler(
            OpenRequests open, FriendRequestRepository requests, FriendNotificationPort notifications, Clock clock) {
        this.open = open;
        this.requests = requests;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean decline(UserId receiver, UUID requestId) {
        return open.lockAddressedTo(receiver, requestId)
                .map(request -> {
                    requests.close(request.id(), RequestStatus.DECLINED, clock.instant());
                    notifications.relationChanged(receiver, request.sender());
                    return true;
                })
                .orElse(false);
    }
}
