package fr.vlegall.seal.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GRP-01 à GRP-04, CNV-06 : règles d'une conversation et d'un groupe")
class ConversationTest {

    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final ConversationId ID =
            new ConversationId(UUID.fromString("0193b000-0000-7000-8000-000000000001"));

    private static UserId user(String hex) {
        return new UserId(UUID.fromString(hex + "-0000-7000-8000-000000000000"));
    }

    // L'ordre non signé met 10000000 avant 90000000 ; l'ordre signé de UUID#compareTo dirait l'inverse.
    private final UserId admin = user("50000000");
    private final UserId small = user("10000000");
    private final UserId large = user("90000000");
    private final GroupName name = GroupName.parse("Ski du dimanche");

    private Conversation group(UserId... others) {
        return Conversation.group(ID, admin, name, new LinkedHashSet<>(List.of(others)), T0);
    }

    private static Set<UserId> users(int count) {
        Set<UserId> result = new LinkedHashSet<>();
        IntStream.range(0, count)
                .forEach(i ->
                        result.add(new UserId(UUID.fromString(String.format("0192f7a1-0000-7000-8000-%012d", i)))));
        return result;
    }

    @Test
    @DisplayName("GRP-01 : le créateur est administrateur ; 2 à 50 membres, créateur compris")
    void grp01CreatorIsAdminOfTwoToFiftyMembers() {
        Conversation conversation = group(small);

        assertThat(conversation.type()).isEqualTo(ConversationType.GROUP);
        assertThat(conversation.admin()).contains(admin);
        assertThat(conversation.activeMembers()).extracting(Member::user).containsExactlyInAnyOrder(admin, small);
        assertThat(Conversation.group(ID, admin, name, users(49), T0).activeMembers())
                .hasSize(50);
        assertThatThrownBy(() -> Conversation.group(ID, admin, name, users(50), T0))
                .isInstanceOf(ConversationException.GroupFull.class);
        assertThatThrownBy(() -> Conversation.group(ID, admin, name, Set.of(), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GRP-03, GRP-04 : tout membre ajoute ses amis ; un membre déjà là est ignoré ; 50 au plus")
    void grp03AnyMemberAddsNewcomersUpToFifty() {
        Conversation conversation = group(small).join(small, Set.of(large, admin), T1);

        assertThat(conversation.activeMembers())
                .extracting(Member::user)
                .containsExactlyInAnyOrder(admin, small, large);
        assertThat(conversation.activeMembers())
                .filteredOn(m -> m.user().equals(large))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.role()).isEqualTo(Role.MEMBER);
                    assertThat(m.joinedAt()).isEqualTo(T1);
                });
        Conversation full = Conversation.group(ID, admin, name, users(49), T0);
        assertThatThrownBy(() -> full.join(admin, Set.of(large), T1))
                .isInstanceOf(ConversationException.GroupFull.class);
    }

    @Test
    @DisplayName("GRP-03 : seul l'administrateur retire et renomme ; il part par leave ; NOT_A_MEMBER hors du groupe")
    void grp03OnlyAdminExcludesAndRenames() {
        Conversation conversation = group(small, large);

        assertThatThrownBy(() -> conversation.exclude(small, large))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> conversation.rename(small, GroupName.parse("Autre")))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> conversation.exclude(admin, admin))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> conversation.exclude(admin, user("70000000")))
                .isInstanceOf(ConversationException.NotAMember.class);
        assertThat(conversation.exclude(admin, large).activeMembers())
                .extracting(Member::user)
                .containsExactlyInAnyOrder(admin, small);
        assertThat(conversation.rename(admin, GroupName.parse("Autre")).name()).contains(GroupName.parse("Autre"));
    }

    @Test
    @DisplayName("GRP-03, ADR 0039 (point 4) : sans administrateur, le plus tôt arrivé, puis le plus petit identifiant")
    void grp03SuccessorIsEarliestThenSmallestIdentifier() {
        UserId latecomer = user("00000001");
        Conversation conversation = group(large, small).join(small, Set.of(latecomer), T1);

        Conversation after = conversation.depart(admin);

        assertThat(after.admin()).contains(small);
        assertThat(after.activeMembers())
                .filteredOn(m -> m.role() == Role.ADMIN)
                .hasSize(1);
    }

    @Test
    @DisplayName("GRP-03, ADR 0039 (point 5) : un groupe vit avec un seul membre ; vide, il n'a plus d'administrateur")
    void grp03GroupLivesWithOneMemberAndHasNoAdminWhenEmpty() {
        Conversation alone = group(small).depart(admin);

        assertThat(alone.admin()).contains(small);
        assertThat(alone.activeMembers()).hasSize(1);
        Conversation empty = alone.depart(small);
        assertThat(empty.activeMembers()).isEmpty();
        assertThat(empty.admin()).isEmpty();
    }

    @Test
    @DisplayName("GRP-01, ADR 0039 (point 3) : une directe refuse ajout, retrait, départ et renommage")
    void grp01DirectRefusesGroupActions() {
        Conversation direct = Conversation.direct(ID, admin, small, T0);

        assertThat(direct.type()).isEqualTo(ConversationType.DIRECT);
        assertThat(direct.name()).isEmpty();
        assertThat(direct.activeMembers()).extracting(Member::role).containsOnly(Role.MEMBER);
        assertThatThrownBy(() -> direct.join(admin, Set.of(large), T1))
                .isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> direct.exclude(admin, small)).isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> direct.depart(admin)).isInstanceOf(ConversationException.Forbidden.class);
        assertThatThrownBy(() -> direct.rename(admin, name)).isInstanceOf(ConversationException.Forbidden.class);
    }

    @Test
    @DisplayName("CNV-06 : un non-membre reçoit NOT_FOUND, pas FORBIDDEN")
    void cnv06NonMemberIsNotFound() {
        Conversation conversation = group(small);

        conversation.ensureMember(small);
        assertThatThrownBy(() -> conversation.ensureMember(large)).isInstanceOf(ConversationException.NotFound.class);
        assertThat(conversation.depart(small).isActiveMember(small)).isFalse();
    }
}
