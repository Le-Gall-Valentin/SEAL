package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.CreateGroupUseCase;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import fr.vlegall.seal.conversations.domain.port.out.FriendshipPort;
import java.time.Clock;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CreateGroupHandler implements CreateGroupUseCase {

    private final ConversationRepository conversations;
    private final FriendshipPort friendships;
    private final ConversationSummaries summaries;
    private final Clock clock;

    public CreateGroupHandler(
            ConversationRepository conversations,
            FriendshipPort friendships,
            ConversationSummaries summaries,
            Clock clock) {
        this.conversations = conversations;
        this.friendships = friendships;
        this.summaries = summaries;
        this.clock = clock;
    }

    /** GRP-02 : tous amis du créateur (FRD-21), sinon rien n'est écrit ; groupe et membres en une transaction. */
    @Override
    @Transactional
    public ConversationSummary create(UserId creator, String name, Set<UserId> memberIds) {
        GroupName groupName = GroupName.parse(name);
        if (!friendships.areAllFriends(creator, memberIds)) {
            throw new ConversationException.NotFriends();
        }
        Conversation group = Conversation.group(conversations.nextId(), creator, groupName, memberIds, clock.instant());
        conversations.createGroup(group);
        return summaries.of(creator, group);
    }
}
