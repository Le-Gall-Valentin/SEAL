package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;

/** CNV-06 : détail et membres d'une conversation, pour un membre actif seulement. */
public interface GetConversationQuery {

    ConversationSummary get(UserId member, ConversationId id);
}
