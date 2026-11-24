package fr.vlegall.seal.conversations.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CNV-04, CNV-06, API-02 : liste, détail et conversation commune")
class ConversationQueriesHandlerTest {

    private final InMemoryConversations store = new InMemoryConversations();
    private final CreateGroupHandler groups =
            new CreateGroupHandler(store.repository(), store.friendshipPort(), store.summaries(), store.clock);
    private final OpenDirectConversationHandler direct = new OpenDirectConversationHandler(
            store.repository(), store.friendshipPort(), store.summaries(), store.clock);
    private final LeaveConversationHandler leave = new LeaveConversationHandler(store.repository(), store.clock);
    private final ListConversationsHandler list = new ListConversationsHandler(store.repository(), store.summaries());
    private final ConversationMembershipHandler membership = new ConversationMembershipHandler(store.repository());

    private final UserId me = store.person("moi");
    private final UserId friend = store.person("ami");
    private final UserId other = store.person("autre");

    ConversationQueriesHandlerTest() {
        store.befriend(me, friend);
        store.befriend(me, other);
    }

    @Test
    @DisplayName("CNV-04 : liste paginée des appartenances actives, activité vide jusqu'au lot 4")
    void cnv04ListPaginatesActiveMembershipsWithEmptyActivity() {
        ConversationId first = groups.create(me, "Un", Set.of(friend)).id();
        ConversationId left = groups.create(me, "Deux", Set.of(friend)).id();
        ConversationId third = groups.create(me, "Trois", Set.of(friend)).id();
        leave.leave(me, left);

        Page<ConversationSummary> page = list.list(me, Optional.empty(), 1);
        Page<ConversationSummary> next = list.list(me, page.next().map(ConversationId::new), 1);

        assertThat(page.items()).extracting(ConversationSummary::id).containsExactly(first);
        assertThat(next.items()).extracting(ConversationSummary::id).containsExactly(third);
        assertThat(next.next()).isEmpty();
        assertThat(page.items()).singleElement().satisfies(c -> {
            assertThat(c.lastServerSeq()).isZero();
            assertThat(c.activity().unreadCount()).isZero();
            assertThat(c.activity().lastSenderSeq()).isEmpty();
        });
    }

    @Test
    @DisplayName("CNV-04, ADR 0039 (point 6) : directe d'un compte inactif omise ; membre inactif absent des membres")
    void cnv04InactiveAccountsAreLeftOut() {
        direct.open(me, friend);
        ConversationId group = groups.create(me, "Ski", Set.of(friend, other)).id();
        store.deactivate(friend);

        Page<ConversationSummary> page = list.list(me, Optional.empty(), 50);

        assertThat(page.items()).extracting(ConversationSummary::id).containsExactly(group);
        assertThat(page.items().getFirst().members())
                .extracting(m -> m.profile().user())
                .containsExactlyInAnyOrder(me, other);
    }

    @Test
    @DisplayName("API-02, ADR 0039 (point 7) : deux membres actifs partagent une conversation ; un ancien membre non")
    void api02ActiveMembersShareAConversation() {
        ConversationId group = groups.create(me, "Ski", Set.of(friend)).id();

        assertThat(membership.shareConversation(me, friend)).isTrue();
        assertThat(membership.shareConversation(me, other)).isFalse();
        leave.leave(friend, group);
        assertThat(membership.shareConversation(me, friend)).isFalse();
    }
}
