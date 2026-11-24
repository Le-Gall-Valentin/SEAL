package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import java.util.Optional;

/** CNV-04, ADR 0039 (point 6) : conversations où le compte est membre actif, paginées par identifiant. */
public interface ListConversationsQuery {

    Page<ConversationSummary> list(UserId member, Optional<ConversationId> after, int limit);
}
