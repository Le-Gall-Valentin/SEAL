package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.RenameGroupUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.port.out.ConversationNotificationPort;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class RenameGroupHandler implements RenameGroupUseCase {

    private final ConversationRepository conversations;
    private final ConversationSummaries summaries;
    private final ConversationNotificationPort notifications;
    private final Clock clock;

    public RenameGroupHandler(
            ConversationRepository conversations,
            ConversationSummaries summaries,
            ConversationNotificationPort notifications,
            Clock clock) {
        this.conversations = conversations;
        this.summaries = summaries;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** GRP-03 : sous le verrou ; 404 pour un non-membre (CNV-06), puis le nom, puis les droits. */
    @Override
    @Transactional
    public ConversationSummary rename(UserId caller, ConversationId id, String name) {
        Conversation before = conversations.lockForMember(id, caller).orElseThrow(ConversationException.NotFound::new);
        Conversation after = before.rename(caller, GroupName.parse(name));
        conversations.save(before, after, clock.instant());
        notifications.changed(id, after.memberIds());
        return summaries.of(caller, after);
    }
}
