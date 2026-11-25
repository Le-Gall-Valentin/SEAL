package fr.vlegall.seal.friends.infrastructure.ws;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.port.out.FriendNotificationPort;
import fr.vlegall.seal.ws.application.port.in.NotifyUsersUseCase;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

/** FRD-04 : les trames {@code friend.request} et {@code friend.update} (CDC 12.2), par le contexte {@code ws}. */
@Component
public class WsFriendNotificationAdapter implements FriendNotificationPort {

    private final NotifyUsersUseCase notify;

    public WsFriendNotificationAdapter(NotifyUsersUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void requestReceived(UserId receiver, UUID requestId) {
        notify.announce(
                Set.of(receiver),
                "friend.request",
                JsonNodeFactory.instance.objectNode().put("requestId", requestId.toString()));
    }

    @Override
    public void relationChanged(UserId one, UserId other) {
        update(one, other);
        update(other, one);
    }

    /** Chaque compte reçoit l'identifiant de l'autre. */
    private void update(UserId recipient, UserId about) {
        notify.announce(
                Set.of(recipient),
                "friend.update",
                JsonNodeFactory.instance
                        .objectNode()
                        .put("userId", about.value().toString()));
    }
}
