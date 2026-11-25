package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.RevokeFriendRequestUseCase;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.port.out.FriendNotificationPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class RevokeFriendRequestHandler implements RevokeFriendRequestUseCase {

    private final OpenRequests open;
    private final FriendRequestRepository requests;
    private final FriendNotificationPort notifications;
    private final Clock clock;

    public RevokeFriendRequestHandler(
            OpenRequests open, FriendRequestRepository requests, FriendNotificationPort notifications, Clock clock) {
        this.open = open;
        this.requests = requests;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean revoke(UserId sender, UUID requestId) {
        return open.lockSentBy(sender, requestId)
                .map(request -> {
                    requests.close(request.id(), RequestStatus.REVOKED, clock.instant());
                    notifications.relationChanged(sender, request.receiver());
                    return true;
                })
                .orElse(false);
    }
}
