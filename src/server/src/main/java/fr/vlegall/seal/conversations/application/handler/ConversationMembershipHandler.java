package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.ConversationMembershipQuery;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ConversationMembershipHandler implements ConversationMembershipQuery {

    private final ConversationRepository conversations;

    public ConversationMembershipHandler(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    /** API-02 : membres actifs d'une même conversation ; un ancien membre ne compte pas (ADR 0039, point 7). */
    @Override
    @Transactional(readOnly = true)
    public boolean shareConversation(UserId a, UserId b) {
        return !a.equals(b) && conversations.shareActiveConversation(a, b);
    }
}
