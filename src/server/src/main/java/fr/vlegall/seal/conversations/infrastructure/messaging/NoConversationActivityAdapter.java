package fr.vlegall.seal.conversations.infrastructure.messaging;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Activity;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.port.out.ConversationActivityPort;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ADR 0039, point 6 : avant la messagerie du lot 4, aucun message n'existe, donc aucune activité. Le lot 4 remplace cet
 * adaptateur par un appel au port d'entrée du contexte {@code messaging}.
 */
@Component
public class NoConversationActivityAdapter implements ConversationActivityPort {

    @Override
    public Map<ConversationId, Activity> activityOf(UserId member, Collection<ConversationId> conversations) {
        return Map.of();
    }
}
