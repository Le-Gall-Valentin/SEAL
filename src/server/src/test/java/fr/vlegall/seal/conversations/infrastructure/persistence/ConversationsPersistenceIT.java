package fr.vlegall.seal.conversations.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.DirectKey;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.model.Member;
import fr.vlegall.seal.conversations.domain.model.Role;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import fr.vlegall.seal.support.SealIntegrationTest;
import fr.vlegall.seal.support.TestRelations;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** CNV-01, CNV-04, GRP-01, GRP-03, API-02 : persistance des conversations et de leurs membres (ADR 0039). */
@SealIntegrationTest
class ConversationsPersistenceIT {

    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private UserId user() {
        return new UserId(TestRelations.activeUser(jdbc));
    }

    private Conversation group(UserId creator, UserId... others) {
        Conversation group = Conversation.group(
                conversations.nextId(), creator, GroupName.parse("Ski du dimanche"), Set.of(others), T0);
        conversations.createGroup(group);
        return group;
    }

    private Conversation reload(ConversationId id, UserId member) {
        return conversations.findForMember(id, member).orElseThrow();
    }

    private static List<String> roles(Conversation conversation) {
        return conversation.activeMembers().stream()
                .map(m -> m.user().value() + ":" + m.role())
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("CNV-01 : une seule directe par paire, quel que soit le sens ; deux membres MEMBER")
    void cnv01OneDirectPerPairWhicheverDirection() {
        UserId a = user();
        UserId b = user();

        assertThat(conversations.createDirect(DirectKey.of(a, b), a, b, T0)).isTrue();
        assertThat(conversations.createDirect(DirectKey.of(b, a), b, a, T1)).isFalse();

        Conversation direct = conversations.findDirect(DirectKey.of(a, b)).orElseThrow();
        assertThat(direct.activeMembers()).extracting(Member::user).containsExactlyInAnyOrder(a, b);
        assertThat(direct.activeMembers()).extracting(Member::role).containsOnly(Role.MEMBER);
        assertThat(jdbc.sql("SELECT count(*) FROM conversations WHERE direct_key = ?")
                        .param(DirectKey.of(a, b).value())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("CNV-01 : deux créations simultanées de la même directe, en sens inverse : une seule la crée")
    void cnv01ConcurrentCreationsMakeOneDirect() {
        UserId a = user();
        UserId b = user();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                () -> transactions.execute(s -> conversations.createDirect(DirectKey.of(a, b), a, b, T0)));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(
                () -> transactions.execute(s -> conversations.createDirect(DirectKey.of(b, a), b, a, T0)));

        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("GRP-01, CNV-06 : le groupe se relit pour un membre, pas pour un tiers")
    void grp01GroupIsReadForMembersOnly() {
        UserId creator = user();
        UserId friend = user();
        Conversation created = group(creator, friend);

        Conversation read = reload(created.id(), friend);

        assertThat(read.name()).contains(GroupName.parse("Ski du dimanche"));
        assertThat(read.admin()).contains(creator);
        assertThat(roles(read)).isEqualTo(roles(created));
        assertThat(conversations.findForMember(created.id(), user())).isEmpty();
    }

    @Test
    @DisplayName("GRP-03 : arrivées, départs, succession et nom sont enregistrés ; un ancien membre peut revenir")
    void grp03ArrivalsDeparturesRolesAndNameAreSaved() {
        UserId creator = user();
        UserId b = user();
        UserId c = user();
        Conversation created = group(creator, b);

        Conversation joined = created.join(b, Set.of(c), T1);
        conversations.save(created, joined, T1);
        Conversation departed = reload(created.id(), b).depart(creator);
        conversations.save(reload(created.id(), b), departed, T2);
        UserId successor = departed.admin().orElseThrow();
        Conversation renamed = reload(created.id(), b).rename(successor, GroupName.parse("Autre nom"));
        conversations.save(reload(created.id(), b), renamed, T2);

        Conversation read = reload(created.id(), b);
        assertThat(read.activeMembers()).extracting(Member::user).containsExactlyInAnyOrder(b, c);
        assertThat(read.admin()).contains(successor);
        assertThat(read.name()).contains(GroupName.parse("Autre nom"));
        assertThat(conversations.findForMember(created.id(), creator)).isEmpty();
        assertThat(jdbc.sql(
                                "SELECT left_at IS NOT NULL FROM conversation_members WHERE conversation_id = ? AND user_id = ?")
                        .param(created.id().value())
                        .param(creator.value())
                        .query(Boolean.class)
                        .single())
                .isTrue();

        Conversation back = read.join(b, Set.of(creator), T2);
        conversations.save(read, back, T2);
        assertThat(reload(created.id(), creator).activeMembers())
                .filteredOn(m -> m.user().equals(creator))
                .singleElement()
                .extracting(Member::role)
                .isEqualTo(Role.MEMBER);
    }

    @Test
    @DisplayName(
            "GRP-03, CNV-07 : l'administrateur parti redevient MEMBER ; un arrivant n'a pas de non-lus d'avant lui")
    void grp03DepartedAdminIsMemberAndNewcomerReadsFromArrival() {
        UserId creator = user();
        UserId b = user();
        UserId c = user();
        Conversation created = group(creator, b);
        jdbc.sql("UPDATE conversations SET last_server_seq = 7 WHERE id = ?")
                .param(created.id().value())
                .update();
        Conversation current = reload(created.id(), creator);
        conversations.save(current, current.join(creator, Set.of(c), T1), T1);
        Conversation withC = reload(created.id(), creator);

        conversations.save(withC, withC.depart(creator), T2);

        MemberRow departed = row(created.id(), creator);
        assertThat(departed.role()).isEqualTo("MEMBER");
        assertThat(departed.leftSeq()).isEqualTo(7L);
        assertThat(row(created.id(), c).lastReadSeq()).isEqualTo(7);
        assertThat(row(created.id(), c).joinedSeq()).isEqualTo(7);
    }

    @Test
    @DisplayName("GRP-03 : un arrivant déjà actif, vu d'un état périmé, garde sa ligne")
    void grp03StaleArrivalOfAnActiveMemberKeepsItsRow() {
        UserId creator = user();
        UserId b = user();
        Conversation created = group(creator, b);
        Conversation stale = created.depart(b);

        conversations.save(stale, stale.join(creator, Set.of(b), T2), T2);

        assertThat(reload(created.id(), b).activeMembers())
                .filteredOn(m -> m.user().equals(b))
                .singleElement()
                .extracting(Member::joinedAt)
                .isEqualTo(T0);
    }

    /** Ligne de {@code conversation_members}, lue telle quelle. */
    private record MemberRow(String role, long joinedSeq, Long leftSeq, long lastReadSeq) {}

    private MemberRow row(ConversationId id, UserId user) {
        return jdbc.sql("SELECT role, joined_seq, left_seq, last_read_seq FROM conversation_members"
                        + " WHERE conversation_id = ? AND user_id = ?")
                .param(id.value())
                .param(user.value())
                .query((rs, n) -> new MemberRow(
                        rs.getString("role"),
                        rs.getLong("joined_seq"),
                        rs.getObject("left_seq", Long.class),
                        rs.getLong("last_read_seq")))
                .single();
    }

    @Test
    @DisplayName("GRP-03 : la conversation verrouillée par une transaction attend la fin de celle-ci")
    void grp03LockedConversationWaitsForTheHolder() throws Exception {
        UserId creator = user();
        UserId friend = user();
        Conversation created = group(creator, friend);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(s -> {
            conversations.lockForMember(created.id(), creator).orElseThrow();
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
                    jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
                    conversations.lockForMember(created.id(), friend);
                }))
                .isInstanceOf(DataAccessException.class);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        Optional<Conversation> afterRelease =
                transactions.execute(s -> conversations.lockForMember(created.id(), friend));
        assertThat(Objects.requireNonNull(afterRelease)).isPresent();
        Optional<Conversation> stranger = transactions.execute(s -> conversations.lockForMember(created.id(), user()));
        assertThat(Objects.requireNonNull(stranger)).isEmpty();
    }

    @Test
    @DisplayName("CNV-06 : un membre exclu pendant qu'il attendait le verrou ne reçoit pas la conversation")
    void cnv06MemberExcludedWhileWaitingForTheLockGetsNothing() throws Exception {
        UserId admin = user();
        UserId member = user();
        Conversation created = group(admin, member);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(s -> {
            Conversation before =
                    conversations.lockForMember(created.id(), admin).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
            conversations.save(before, before.exclude(admin, member), T1);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Optional<Conversation>> waiting = CompletableFuture.supplyAsync(() ->
                Objects.requireNonNull(transactions.execute(s -> conversations.lockForMember(created.id(), member))));
        awaitLockWaiter();
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        // READ COMMITTED : la sous-requête d'appartenance a vu l'instantané d'avant l'attente ; les membres relus, non.
        assertThat(waiting.get(10, TimeUnit.SECONDS)).isEmpty();
    }

    /** Attend qu'une transaction soit bloquée sur le verrou d'une conversation. */
    private void awaitLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                + " AND query LIKE '%FOR UPDATE OF c%'")
                        .query(Long.class)
                        .single()
                == 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("aucune transaction n'attend le verrou");
            }
            Thread.sleep(10);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("CNV-04 : la page d'un membre suit les identifiants, sans les conversations qu'il a quittées")
    void cnv04PageFollowsIdentifiersWithoutDepartedConversations() {
        UserId member = user();
        UserId other = user();
        Conversation first = group(member, other);
        Conversation second = group(other, member);
        Conversation third = group(member, other);
        conversations.save(second, second.depart(member), T1);

        List<Conversation> page = conversations.pageForMember(member, Optional.empty(), 10);
        assertThat(page).extracting(Conversation::id).containsExactly(first.id(), third.id());
        List<Conversation> next = conversations.pageForMember(member, Optional.of(first.id()), 10);
        assertThat(next).extracting(Conversation::id).containsExactly(third.id());
        assertThat(conversations.pageForMember(member, Optional.empty(), 1)).hasSize(1);
    }

    @Test
    @DisplayName("API-02 : deux membres actifs d'un même groupe le partagent ; plus après un départ")
    void api02ActiveMembersShareAConversation() {
        UserId a = user();
        UserId b = user();
        Conversation created = group(a, b);

        assertThat(conversations.shareActiveGroup(a, b)).isTrue();
        assertThat(conversations.shareActiveGroup(a, user())).isFalse();
        conversations.save(created, created.depart(b), T1);
        assertThat(conversations.shareActiveGroup(a, b)).isFalse();
    }

    @Test
    @DisplayName("API-02, ADR 0039 (point 7) : une conversation directe ne compte pas comme groupe commun")
    void api02DirectConversationIsNotACommonGroup() {
        UserId a = user();
        UserId b = user();
        conversations.createDirect(DirectKey.of(a, b), a, b, T0);

        assertThat(conversations.shareActiveGroup(a, b)).isFalse();
        assertThat(conversations.shareActiveGroup(b, a)).isFalse();
    }
}
