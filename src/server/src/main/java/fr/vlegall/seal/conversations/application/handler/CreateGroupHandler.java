package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.CreateGroupUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CreateGroupHandler implements CreateGroupUseCase {

    private final ConversationRepository conversations;
    private final ActiveFriends friends;
    private final ConversationSummaries summaries;
    private final Clock clock;

    public CreateGroupHandler(
            ConversationRepository conversations, ActiveFriends friends, ConversationSummaries summaries, Clock clock) {
        this.conversations = conversations;
        this.friends = friends;
        this.summaries = summaries;
        this.clock = clock;
    }

    /** GRP-02 : tous amis actifs du créateur (FRD-21), sinon rien n'est écrit ; groupe et membres en une transaction. */
    @Override
    @Transactional
    public ConversationSummary create(UserId creator, String name, Set<UserId> memberIds) {
        GroupName groupName = GroupName.parse(name);
        friends.require(creator, memberIds);
        Conversation group = Conversation.group(conversations.nextId(), creator, groupName, memberIds, clock.instant());
        conversations.createGroup(group);
        // Relu tel que GET le rendra : ordre des membres et instants à la précision de la base.
        return summaries.of(
                creator, conversations.findForMember(group.id(), creator).orElseThrow());
    }
}
