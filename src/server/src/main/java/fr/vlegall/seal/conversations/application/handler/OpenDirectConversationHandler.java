package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.OpenDirectConversationUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.DirectKey;
import fr.vlegall.seal.conversations.domain.model.OpenedDirect;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class OpenDirectConversationHandler implements OpenDirectConversationUseCase {

    private final ConversationRepository conversations;
    private final ActiveFriends friends;
    private final ConversationSummaries summaries;
    private final Clock clock;

    public OpenDirectConversationHandler(
            ConversationRepository conversations, ActiveFriends friends, ConversationSummaries summaries, Clock clock) {
        this.conversations = conversations;
        this.friends = friends;
        this.summaries = summaries;
        this.clock = clock;
    }

    /**
     * CNV-01 : l'amitié est revérifiée (FRD-21) ; un non-ami, soi-même, un ancien ami ou un compte inactif reçoit le même
     * refus, sans écriture (ADR 0039, point 2). Deux demandes simultanées n'en créent qu'une : l'index unique tranche.
     */
    @Override
    @Transactional
    public OpenedDirect open(UserId caller, UserId other) {
        friends.require(caller, Set.of(other));
        DirectKey key = DirectKey.of(caller, other);
        boolean created = conversations.createDirect(key, caller, other, clock.instant());
        Conversation direct = conversations.findDirect(key).orElseThrow();
        return new OpenedDirect(created, summaries.of(caller, direct));
    }
}
