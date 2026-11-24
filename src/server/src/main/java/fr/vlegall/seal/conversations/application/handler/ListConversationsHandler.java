package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.ListConversationsQuery;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ListConversationsHandler implements ListConversationsQuery {

    private final ConversationRepository conversations;
    private final ConversationSummaries summaries;

    public ListConversationsHandler(ConversationRepository conversations, ConversationSummaries summaries) {
        this.conversations = conversations;
        this.summaries = summaries;
    }

    /** CNV-04 : une page peut être courte quand une directe est omise ; seule {@code next} vide marque la fin. */
    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummary> list(UserId member, Optional<ConversationId> after, int limit) {
        Page<Conversation> raw =
                Page.cut(conversations.pageForMember(member, after, limit + 1), limit, c -> c.id().value());
        return raw.withItems(summaries.listed(member, raw.items()));
    }
}
