package fr.vlegall.seal.conversations.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.ConversationType;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.model.MemberSummary;
import fr.vlegall.seal.conversations.domain.model.OpenedDirect;
import fr.vlegall.seal.conversations.domain.model.Role;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CNV-01, CNV-03, GRP-02, FRD-21 : ouvrir une directe, créer un groupe")
class OpenDirectAndCreateGroupHandlerTest {

    private final InMemoryConversations store = new InMemoryConversations();
    private final OpenDirectConversationHandler direct = new OpenDirectConversationHandler(
            store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final CreateGroupHandler groups =
            new CreateGroupHandler(store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final GetConversationHandler get = new GetConversationHandler(store.repository(), store.summaries());
    private final UserId me = store.person("moi");
    private final UserId friend = store.person("ami");
    private final UserId other = store.person("autre.ami");
    private final UserId stranger = store.person("inconnu");

    OpenDirectAndCreateGroupHandlerTest() {
        store.befriend(me, friend);
        store.befriend(me, other);
    }

    @Test
    @DisplayName("CNV-01 : trouver ou créer ; la seconde demande, dans l'autre sens, rend la même directe")
    void cnv01FindOrCreateTheSameDirect() {
        OpenedDirect first = direct.open(me, friend);
        OpenedDirect second = direct.open(friend, me);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.conversation().id()).isEqualTo(first.conversation().id());
        assertThat(first.conversation().type()).isEqualTo(ConversationType.DIRECT);
        assertThat(first.conversation().members())
                .extracting(m -> m.profile().user())
                .containsExactlyInAnyOrder(me, friend);
    }

    @Test
    @DisplayName(
            "FRD-21, ADR 0039 (point 2) : non-ami, soi-même et ancien ami reçoivent le même NOT_FRIENDS, sans écriture")
    void frd21NonFriendSelfAndFormerFriendAreRefusedAlike() {
        OpenedDirect opened = direct.open(me, friend);
        store.unfriend(me, friend);

        assertThatThrownBy(() -> direct.open(me, stranger)).isInstanceOf(ConversationException.NotFriends.class);
        assertThatThrownBy(() -> direct.open(me, me)).isInstanceOf(ConversationException.NotFriends.class);
        assertThatThrownBy(() -> direct.open(me, friend)).isInstanceOf(ConversationException.NotFriends.class);
        assertThat(store.byId).hasSize(1);
        // FRD-11 : la directe d'un ancien ami reste lisible.
        assertThat(get.get(me, opened.conversation().id()).id())
                .isEqualTo(opened.conversation().id());
    }

    @Test
    @DisplayName("FRD-21, ADR 0039 (point 2) : un ami au compte inactif reçoit le même NOT_FRIENDS, sans écriture")
    void frd21InactiveFriendIsRefusedLikeAStranger() {
        store.deactivate(friend);

        assertThatThrownBy(() -> direct.open(me, friend)).isInstanceOf(ConversationException.NotFriends.class);
        assertThatThrownBy(() -> groups.create(me, "Ski", Set.of(friend, other)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThat(store.byId).isEmpty();
    }

    @Test
    @DisplayName("GRP-02, CNV-03 : un groupe d'amis du créateur, en une fois ; le créateur est administrateur")
    void grp02GroupOfFriendsWithCreatorAsAdmin() {
        ConversationSummary group = groups.create(me, "Ski du dimanche", Set.of(friend, other));

        assertThat(group.type()).isEqualTo(ConversationType.GROUP);
        assertThat(group.name()).contains(GroupName.parse("Ski du dimanche"));
        assertThat(group.members())
                .filteredOn(m -> m.role() == Role.ADMIN)
                .extracting(m -> m.profile().user())
                .containsExactly(me);
        assertThat(group.members()).extracting(MemberSummary::role).containsOnly(Role.ADMIN, Role.MEMBER);
        assertThat(group.members()).hasSize(3);
    }

    @Test
    @DisplayName("FRD-21, GRP-02 : un seul non-ami, ou le créateur lui-même, refuse tout le groupe, sans écriture")
    void frd21OneNonFriendRefusesTheWholeGroup() {
        assertThatThrownBy(() -> groups.create(me, "Ski", Set.of(friend, stranger)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThatThrownBy(() -> groups.create(me, "Ski", Set.of(friend, me)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThatThrownBy(() -> groups.create(me, "", Set.of(friend)))
                .isInstanceOf(ConversationException.InvalidName.class);
        assertThat(store.byId).isEmpty();
    }
}
