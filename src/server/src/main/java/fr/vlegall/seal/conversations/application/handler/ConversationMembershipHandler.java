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

    /** API-02 : membres actifs d'un même groupe ; ni un ancien membre ni une directe ne comptent (ADR 0039, point 7). */
    @Override
    @Transactional(readOnly = true)
    public boolean shareGroup(UserId a, UserId b) {
        return !a.equals(b) && conversations.shareActiveGroup(a, b);
    }
}
