package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.AddMembersUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import fr.vlegall.seal.conversations.domain.port.out.FriendshipPort;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class AddMembersHandler implements AddMembersUseCase {

    private final ConversationRepository conversations;
    private final FriendshipPort friendships;
    private final ConversationSummaries summaries;
    private final Clock clock;

    public AddMembersHandler(
            ConversationRepository conversations,
            FriendshipPort friendships,
            ConversationSummaries summaries,
            Clock clock) {
        this.conversations = conversations;
        this.friendships = friendships;
        this.summaries = summaries;
        this.clock = clock;
    }

    /**
     * GRP-03 : sous le verrou de la conversation, les arrivants (hors membres actifs, ignorés) doivent tous être amis de
     * l'appelant (FRD-21) ; deux ajouts simultanés ne dépassent jamais 50 membres.
     */
    @Override
    @Transactional
    public ConversationSummary add(UserId caller, ConversationId id, Set<UserId> newcomers) {
        Conversation before = conversations.lockForMember(id, caller).orElseThrow(ConversationException.NotFound::new);
        before.ensureGroup();
        Set<UserId> arriving =
                newcomers.stream().filter(user -> !before.isActiveMember(user)).collect(Collectors.toSet());
        if (!friendships.areAllFriends(caller, arriving)) {
            throw new ConversationException.NotFriends();
        }
        Conversation after = before.join(caller, arriving, clock.instant());
        conversations.save(before, after, clock.instant());
        return summaries.of(caller, after);
    }
}
