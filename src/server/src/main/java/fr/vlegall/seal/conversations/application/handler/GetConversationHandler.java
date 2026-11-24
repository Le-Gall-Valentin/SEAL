package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.GetConversationQuery;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class GetConversationHandler implements GetConversationQuery {

    private final ConversationRepository conversations;
    private final ConversationSummaries summaries;

    public GetConversationHandler(ConversationRepository conversations, ConversationSummaries summaries) {
        this.conversations = conversations;
        this.summaries = summaries;
    }

    /** CNV-06 : un non-membre reçoit 404, comme pour une conversation inconnue. */
    @Override
    @Transactional(readOnly = true)
    public ConversationSummary get(UserId member, ConversationId id) {
        return summaries.of(
                member, conversations.findForMember(id, member).orElseThrow(ConversationException.NotFound::new));
    }
}
