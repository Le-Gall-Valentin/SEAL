package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import java.util.Set;

/** CNV-04, ADR 0043 : {@code conv.update} aux comptes concernés, anciens membres compris. */
@FunctionalInterface
public interface ConversationNotificationPort {

    void changed(ConversationId conversation, Set<UserId> users);
}
