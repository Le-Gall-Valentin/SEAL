package fr.vlegall.seal.conversations.infrastructure.ws;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.port.out.ConversationNotificationPort;
import fr.vlegall.seal.ws.application.port.in.NotifyUsersUseCase;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

/** CNV-04 : la trame {@code conv.update} (CDC 12.2), par le contexte {@code ws}. */
@Component
public class WsConversationNotificationAdapter implements ConversationNotificationPort {

    private final NotifyUsersUseCase notify;

    public WsConversationNotificationAdapter(NotifyUsersUseCase notify) {
        this.notify = notify;
    }

    @Override
    public void changed(ConversationId conversation, Set<UserId> users) {
        notify.announce(
                users,
                "conv.update",
                JsonNodeFactory.instance
                        .objectNode()
                        .put("conversationId", conversation.value().toString()));
    }
}
