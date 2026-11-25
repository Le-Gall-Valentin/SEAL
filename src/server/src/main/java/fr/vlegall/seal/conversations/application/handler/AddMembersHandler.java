package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.AddMembersUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationNotificationPort;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class AddMembersHandler implements AddMembersUseCase {

    private final ConversationRepository conversations;
    private final ActiveFriends friends;
    private final ConversationSummaries summaries;
    private final ConversationNotificationPort notifications;
    private final Clock clock;

    public AddMembersHandler(
            ConversationRepository conversations,
            ActiveFriends friends,
            ConversationSummaries summaries,
            ConversationNotificationPort notifications,
            Clock clock) {
        this.conversations = conversations;
        this.friends = friends;
        this.summaries = summaries;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * GRP-03 : sous le verrou de la conversation, les membres actifs (appelant compris) sont ignorés avant le contrôle
     * d'amitié (ADR 0039, point 3) ; les arrivants doivent tous être amis actifs de l'appelant (FRD-21). Deux ajouts
     * simultanés ne dépassent jamais 50 membres.
     */
    @Override
    @Transactional
    public ConversationSummary admit(UserId caller, ConversationId id, Set<UserId> newcomers) {
        Conversation before = conversations.lockForMember(id, caller).orElseThrow(ConversationException.NotFound::new);
        before.ensureGroup();
        Set<UserId> arriving =
                newcomers.stream().filter(user -> !before.isActiveMember(user)).collect(Collectors.toSet());
        friends.require(caller, arriving);
        Instant at = clock.instant();
        Conversation after = before.join(caller, arriving, at);
        conversations.save(before, after, at);
        if (!arriving.isEmpty()) {
            // GRP-03, CNV-04 : l'arrivant et chaque membre.
            notifications.changed(id, after.memberIds());
        }
        // Relu tel que GET le rendra : ordre des membres et instants à la précision de la base.
        return summaries.of(caller, conversations.findForMember(id, caller).orElseThrow());
    }
}
