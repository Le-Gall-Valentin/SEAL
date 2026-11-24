package fr.vlegall.seal.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.AddMembersUseCase;
import fr.vlegall.seal.conversations.application.port.in.CreateGroupUseCase;
import fr.vlegall.seal.conversations.application.port.in.ExcludeMemberUseCase;
import fr.vlegall.seal.conversations.application.port.in.LeaveConversationUseCase;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.support.LockWaits;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.SealIntegrationTest;
import fr.vlegall.seal.support.TestRelations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GRP-01, GRP-03, CNV-06, A06 : courses entre actions de groupe, sous le verrou de la conversation (ADR 0039). Chacune
 * échoue sans le verrou.
 */
@SealIntegrationTest
class ConversationRacesIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CreateGroupUseCase groups;

    @Autowired
    private AddMembersUseCase add;

    @Autowired
    private ExcludeMemberUseCase exclude;

    @Autowired
    private LeaveConversationUseCase leave;

    @Autowired
    private TransactionTemplate transactions;

    private UserId user() {
        return new UserId(TestRelations.activeUser(jdbc));
    }

    private Set<UserId> friendsOf(UserId user, int count) {
        Set<UserId> friends = new HashSet<>();
        IntStream.range(0, count).forEach(i -> {
            UserId friend = user();
            TestRelations.befriend(jdbc, user.value(), friend.value());
            friends.add(friend);
        });
        return friends;
    }

    private List<String> activeRoles(ConversationId id) {
        return jdbc.sql("SELECT role FROM conversation_members WHERE conversation_id = ? AND left_at IS NULL")
                .param(id.value())
                .query(String.class)
                .list();
    }

    private boolean isActive(ConversationId id, UserId user) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?"
                        + " AND left_at IS NULL)")
                .param(id.value())
                .param(user.value())
                .query(Boolean.class)
                .single();
    }

    @RepeatedTest(3)
    @DisplayName("GRP-01, A06 : deux ajouts simultanés ne dépassent jamais 50 membres actifs")
    void grp01ConcurrentAdditionsNeverExceedFifty() throws Exception {
        UserId admin = user();
        ConversationId id = groups.create(admin, "Ski", friendsOf(admin, 47)).id();
        List<Set<UserId>> batches = List.of(friendsOf(admin, 2), friendsOf(admin, 2));

        List<String> outcomes = Race.run(2, i -> {
            try {
                add.admit(admin, id, batches.get(i));
                return "ajouté";
            } catch (ConversationException.GroupFull e) {
                return "plein";
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("ajouté", "plein");
        assertThat(activeRoles(id)).hasSize(50);
    }

    @RepeatedTest(3)
    @DisplayName("GRP-03, A06 : l'administrateur et son successeur partent ensemble ; le dernier membre devient ADMIN")
    void grp03AdminAndSuccessorLeavingTogetherLeaveOneAdmin() throws Exception {
        UserId admin = user();
        List<UserId> members =
                friendsOf(admin, 2).stream().sorted(UserId.POSTGRES_ORDER).toList();
        UserId successor = members.getFirst();
        UserId last = members.getLast();
        ConversationId id = groups.create(admin, "Ski", Set.copyOf(members)).id();

        Race.run(2, i -> {
            leave.leave(i == 0 ? admin : successor, id);
            return null;
        });

        assertThat(activeRoles(id)).containsExactly("ADMIN");
        assertThat(isActive(id, last)).isTrue();
    }

    @Test
    @DisplayName("CNV-06, GRP-03, A06 : un membre retiré pendant qu'il attend le verrou n'ajoute personne")
    void cnv06MemberExcludedWhileWaitingToAddAddsNobody() throws Exception {
        UserId admin = user();
        UserId member = friendsOf(admin, 1).iterator().next();
        UserId newcomer = friendsOf(member, 1).iterator().next();
        ConversationId id = groups.create(admin, "Ski", Set.of(member)).id();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> exclusion = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(s -> {
            exclude.exclude(admin, id, member);
            locked.countDown();
            LockWaits.awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> addition = CompletableFuture.runAsync(() -> add.admit(member, id, Set.of(newcomer)));
        // Sans verrou, l'ajout n'attendrait pas : le test échoue ici.
        LockWaits.awaitWaiter(jdbc, "FOR UPDATE OF c");
        release.countDown();
        exclusion.get(10, TimeUnit.SECONDS);

        assertThatThrownBy(() -> addition.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ConversationException.NotFound.class);
        assertThat(isActive(id, member)).isFalse();
        assertThat(isActive(id, newcomer)).isFalse();
    }
}
