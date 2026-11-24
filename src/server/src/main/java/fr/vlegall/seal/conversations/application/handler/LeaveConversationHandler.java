package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.LeaveConversationUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class LeaveConversationHandler implements LeaveConversationUseCase {

    private final ConversationRepository conversations;
    private final Clock clock;

    public LeaveConversationHandler(ConversationRepository conversations, Clock clock) {
        this.conversations = conversations;
        this.clock = clock;
    }

    /** GRP-03 : sous le verrou, le départ et, s'il le faut, la succession de l'administrateur (ADR 0039, point 4). */
    @Override
    @Transactional
    public void leave(UserId caller, ConversationId id) {
        Conversation before = conversations.lockForMember(id, caller).orElseThrow(ConversationException.NotFound::new);
        conversations.save(before, before.depart(caller), clock.instant());
    }
}
