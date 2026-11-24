package fr.vlegall.seal.conversations.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.model.Role;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GRP-01, GRP-03, GRP-04, CNV-06 : ajouter, retirer, partir, renommer")
class GroupMembershipHandlersTest {

    private final InMemoryConversations store = new InMemoryConversations();
    private final CreateGroupHandler groups =
            new CreateGroupHandler(store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final OpenDirectConversationHandler direct = new OpenDirectConversationHandler(
            store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final AddMembersHandler add =
            new AddMembersHandler(store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final ExcludeMemberHandler exclude = new ExcludeMemberHandler(store.repository(), store.clock);
    private final LeaveConversationHandler leave = new LeaveConversationHandler(store.repository(), store.clock);
    private final RenameGroupHandler rename =
            new RenameGroupHandler(store.repository(), store.summaries(), store.clock);
    private final GetConversationHandler get = new GetConversationHandler(store.repository(), store.summaries());

    private final UserId admin = store.person("admin");
    private final UserId member = store.person("membre");
    private final UserId membersFriend = store.person("ami.du.membre");
    private final UserId adminsFriend = store.person("ami.de.admin");
    private final UserId stranger = store.person("inconnu");
    private final ConversationId group;

    GroupMembershipHandlersTest() {
        store.befriend(admin, member);
        store.befriend(member, membersFriend);
        store.befriend(admin, adminsFriend);
        group = groups.create(admin, "Ski", Set.of(member)).id();
    }

    @Test
    @DisplayName("GRP-03, GRP-04 : un membre ajoute ses propres amis, qui n'ont pas à être amis des autres")
    void grp03MemberAddsOwnFriends() {
        ConversationSummary after = add.admit(member, group, Set.of(membersFriend));

        assertThat(after.members()).extracting(m -> m.profile().user()).contains(membersFriend);
    }

    @Test
    @DisplayName(
            "GRP-04, ADR 0039 (point 3) : un membre déjà là, même non ami de l'appelant, et l'appelant sont ignorés")
    void grp04ActiveMembersAreIgnoredBeforeTheFriendshipCheck() {
        add.admit(member, group, Set.of(membersFriend));

        ConversationSummary after = add.admit(admin, group, Set.of(membersFriend, admin, adminsFriend));
        ConversationSummary unchanged = add.admit(admin, group, Set.of(member, admin));

        assertThat(after.members())
                .extracting(m -> m.profile().user())
                .containsExactlyInAnyOrder(admin, member, membersFriend, adminsFriend);
        assertThat(unchanged.members()).hasSize(4);
    }

    @Test
    @DisplayName(
            "FRD-21, ADR 0039 (point 2) : un ajout mêlant un ami et un non-ami est refusé en entier, sans écriture")
    void frd21MixedAdditionIsRefusedWhole() {
        assertThatThrownBy(() -> add.admit(member, group, Set.of(membersFriend, adminsFriend)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThat(store.byId.get(group).isActiveMember(membersFriend)).isFalse();
        assertThat(store.byId.get(group).activeMembers()).hasSize(2);
    }

    @Test
    @DisplayName("FRD-21, GRP-03 : un arrivant doit être ami de celui qui l'ajoute")
    void frd21NewcomerMustBeAFriendOfTheCaller() {
        assertThatThrownBy(() -> add.admit(member, group, Set.of(adminsFriend)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThat(get.get(admin, group).members()).hasSize(2);
    }

    @Test
    @DisplayName("FRD-21, ADR 0039 (point 2) : un ami au compte inactif n'est pas ajouté")
    void frd21InactiveFriendIsNotAdmitted() {
        store.deactivate(adminsFriend);

        assertThatThrownBy(() -> add.admit(admin, group, Set.of(adminsFriend)))
                .isInstanceOf(ConversationException.NotFriends.class);
        assertThat(store.byId.get(group).activeMembers()).hasSize(2);
    }

    @Test
    @DisplayName("GRP-01 : au-delà de 50 membres actifs, GROUP_FULL")
    void grp01GroupFullBeyondFiftyMembers() {
        Set<UserId> friends = new LinkedHashSet<>();
        IntStream.range(0, 50).forEach(i -> {
            UserId user = store.person("ami." + i);
            store.befriend(admin, user);
            friends.add(user);
        });
        Set<UserId> first = new LinkedHashSet<>(friends.stream().limit(48).toList());
        add.admit(admin, group, first);

        assertThatThrownBy(() -> add.admit(
                        admin,
                        group,
                        Set.of(
                                friends.stream().skip(48).findFirst().orElseThrow(),
                                friends.stream().skip(49).findFirst().orElseThrow())))
                .isInstanceOf(ConversationException.GroupFull.class);
        assertThat(get.get(admin, group).members()).hasSize(50);
    }

    @Test
    @DisplayName("GRP-03 : seul l'administrateur retire et renomme ; à son départ, un successeur est désigné")
    void grp03AdminRightsAndSuccession() {
        add.admit(member, group, Set.of(membersFriend));

        assertThatThrownBy(() -> exclude.exclude(member, group, membersFriend))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> rename.rename(member, group, "Autre"))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> exclude.exclude(admin, group, stranger))
                .isInstanceOf(ConversationException.NotAMember.class);
        assertThat(rename.rename(admin, group, "Autre").name()).contains(GroupName.parse("Autre"));
        exclude.exclude(admin, group, membersFriend);
        leave.leave(admin, group);

        ConversationSummary after = get.get(member, group);
        assertThat(after.members()).singleElement().satisfies(m -> {
            assertThat(m.profile().user()).isEqualTo(member);
            assertThat(m.role()).isEqualTo(Role.ADMIN);
        });
    }

    @Test
    @DisplayName("CNV-06 : un non-membre reçoit NOT_FOUND pour toute action")
    void cnv06NonMemberGetsNotFound() {
        assertThatThrownBy(() -> add.admit(stranger, group, Set.of(member)))
                .isInstanceOf(ConversationException.NotFound.class);
        assertThatThrownBy(() -> exclude.exclude(stranger, group, member))
                .isInstanceOf(ConversationException.NotFound.class);
        assertThatThrownBy(() -> leave.leave(stranger, group)).isInstanceOf(ConversationException.NotFound.class);
        assertThatThrownBy(() -> rename.rename(stranger, group, "Autre"))
                .isInstanceOf(ConversationException.NotFound.class);
        assertThatThrownBy(() -> get.get(stranger, group)).isInstanceOf(ConversationException.NotFound.class);
    }

    @Test
    @DisplayName("GRP-01, ADR 0039 (point 3) : une directe refuse ajout, retrait, départ et renommage")
    void grp01DirectRefusesGroupActions() {
        ConversationId pair = direct.open(admin, member).conversation().id();

        assertThatThrownBy(() -> add.admit(admin, pair, Set.of(adminsFriend)))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> exclude.exclude(admin, pair, member))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> leave.leave(admin, pair)).isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> rename.rename(admin, pair, "Autre"))
                .isInstanceOf(ConversationException.Forbidden.class);
    }
}
