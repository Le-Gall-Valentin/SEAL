package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.ExcludeMemberUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ExcludeMemberHandler implements ExcludeMemberUseCase {

    private final ConversationRepository conversations;
    private final Clock clock;

    public ExcludeMemberHandler(ConversationRepository conversations, Clock clock) {
        this.conversations = conversations;
        this.clock = clock;
    }

    /** GRP-03 : sous le verrou ; 404 pour un non-membre (CNV-06), puis les droits du point 3 de l'ADR 0039. */
    @Override
    @Transactional
    public void exclude(UserId caller, ConversationId id, UserId target) {
        Conversation before = conversations.lockForMember(id, caller).orElseThrow(ConversationException.NotFound::new);
        conversations.save(before, before.exclude(caller, target), clock.instant());
    }
}
