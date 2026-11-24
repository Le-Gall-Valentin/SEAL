package fr.vlegall.seal.friends.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.BlockRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** FRD-01, FRD-10, FRD-11 : persistance des invitations, des amitiés et des blocages. */
@SealIntegrationTest
class FriendsPersistenceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired
    private FriendRequestRepository requests;

    @Autowired
    private FriendshipRepository friendships;

    @Autowired
    private BlockRepository blocks;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private UserId user() {
        String username = "amis.p" + SEQUENCE.incrementAndGet() + "."
                + UUID.randomUUID().toString().substring(0, 6);
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name, email, status) "
                        + "VALUES (?, 'Nom', CAST(? AS citext), 'ACTIVE') RETURNING id")
                .param(username)
                .param(username + "@exemple.ca")
                .query(UUID.class)
                .single());
    }

    private Optional<FriendRequest> lockPending(UUID id) {
        Optional<FriendRequest> locked = transactions.execute(status -> requests.lockPending(id));
        return Objects.requireNonNull(locked);
    }

    /** Ordre des {@code uuid} de PostgreSQL : octets non signés. */
    private static int postgresOrder(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    @Test
    @DisplayName("FRD-01 : une seule invitation en attente par paire, quel que soit le sens")
    void frd01OnePendingRequestPerPair() {
        UserId a = user();
        UserId b = user();

        FriendRequest first = requests.createPending(a, b, NOW).orElseThrow();

        assertThat(first.sender()).isEqualTo(a);
        assertThat(first.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(first.createdAt()).isEqualTo(NOW);
        assertThat(requests.createPending(b, a, NOW)).isEmpty();
        assertThat(requests.createPending(a, b, NOW)).isEmpty();
        assertThat(requests.pendingBetween(UserPair.of(b, a))).contains(first);
    }

    @Test
    @DisplayName("ADR 0037 : la date rendue à la création est celle que la base garde, à la microseconde")
    void adr0037CreatedAtIsTheStoredValue() {
        UserId a = user();
        UserId b = user();

        FriendRequest created =
                requests.createPending(a, b, NOW.plusNanos(123_456_789)).orElseThrow();

        // PostgreSQL garde la microseconde, arrondie : .123456789 devient .123457.
        assertThat(created.createdAt()).isEqualTo(NOW.plusNanos(123_457_000));
        assertThat(requests.pendingBetween(UserPair.of(a, b))).contains(created);
    }

    @Test
    @DisplayName("FRD-01 : le verrou de paire exige une transaction (échec fermé)")
    void frd01PairLockRequiresATransaction() {
        UserPair pair = UserPair.of(user(), user());

        assertThatThrownBy(() -> requests.lockPair(pair))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        transactions.executeWithoutResult(status -> requests.lockPair(pair));
    }

    @Test
    @DisplayName("FRD-01 : une invitation close libère la paire pour une nouvelle invitation")
    void frd01ClosedRequestFreesThePair() {
        UserId a = user();
        UserId b = user();
        FriendRequest first = requests.createPending(a, b, NOW).orElseThrow();

        transactions.executeWithoutResult(status -> requests.close(first.id(), RequestStatus.ACCEPTED, NOW));

        assertThat(requests.pendingBetween(UserPair.of(a, b))).isEmpty();
        assertThat(requests.createPending(b, a, NOW)).isPresent();
    }

    @Test
    @DisplayName("FRD-01 : seule une invitation en attente se verrouille ; clore une invitation close échoue")
    void frd01OnlyPendingRequestsAreLockedAndClosed() {
        UserId a = user();
        UserId b = user();
        FriendRequest request = requests.createPending(a, b, NOW).orElseThrow();

        assertThat(lockPending(request.id())).contains(request);
        transactions.executeWithoutResult(status -> requests.close(request.id(), RequestStatus.DECLINED, NOW));

        assertThat(lockPending(request.id())).isEmpty();
        assertThat(lockPending(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> requests.close(request.id(), RequestStatus.REVOKED, NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FRD-01 : invitations reçues et envoyées, en attente seulement, paginées par identifiant")
    void frd01PendingRequestsByDirection() {
        UserId me = user();
        UserId x = user();
        UserId y = user();
        UserId z = user();
        FriendRequest fromX = requests.createPending(x, me, NOW).orElseThrow();
        FriendRequest fromY = requests.createPending(y, me, NOW).orElseThrow();
        FriendRequest toZ = requests.createPending(me, z, NOW).orElseThrow();
        UserId w = user();
        FriendRequest declined = requests.createPending(w, me, NOW).orElseThrow();
        transactions.executeWithoutResult(status -> requests.close(declined.id(), RequestStatus.DECLINED, NOW));

        List<FriendRequest> firstPage = requests.pending(me, Direction.INCOMING, Optional.empty(), 1);
        List<FriendRequest> secondPage = requests.pending(
                me, Direction.INCOMING, Optional.of(firstPage.getFirst().id()), 5);

        assertThat(firstPage).hasSize(1);
        assertThat(secondPage).hasSize(1).doesNotContainAnyElementsOf(firstPage);
        assertThat(List.of(firstPage.getFirst(), secondPage.getFirst())).containsExactlyInAnyOrder(fromX, fromY);
        assertThat(firstPage.getFirst().id())
                .isEqualTo(java.util.stream.Stream.of(fromX, fromY)
                        .map(FriendRequest::id)
                        .min(FriendsPersistenceIT::postgresOrder)
                        .orElseThrow());
        assertThat(requests.pending(me, Direction.OUTGOING, Optional.empty(), 5))
                .containsExactly(toZ);
    }

    @Test
    @DisplayName("FRD-10, FRD-11 : amitié créée une fois, listée des deux côtés avec sa date, retirée pour les deux")
    void frd10FriendshipLifecycle() {
        UserId a = user();
        UserId b = user();
        UserPair pair = UserPair.of(a, b);

        assertThat(friendships.create(pair, NOW)).isTrue();
        assertThat(friendships.create(pair, NOW.plusSeconds(5))).isFalse();
        assertThat(friendships.exists(UserPair.of(b, a))).isTrue();
        // FRD-21 : combien de ces comptes sont amis de a, dans un sens ou l'autre, en une requête.
        assertThat(friendships.countAmong(a, List.of(b, user(), a))).isEqualTo(1);
        assertThat(friendships.countAmong(b, List.of(a))).isEqualTo(1);
        assertThat(friendships.of(a, Optional.empty(), 10)).containsExactly(new Friend(b, NOW));
        assertThat(friendships.of(b, Optional.empty(), 10)).containsExactly(new Friend(a, NOW));

        assertThat(friendships.dissolve(UserPair.of(b, a))).isTrue();
        assertThat(friendships.dissolve(pair)).isFalse();
        assertThat(friendships.exists(pair)).isFalse();
        assertThat(friendships.of(a, Optional.empty(), 10)).isEmpty();
    }

    @Test
    @DisplayName("FRD-10 : la liste d'amis se pagine par identifiant d'ami, sans doublon ni trou")
    void frd10FriendsArePaginatedByFriendId() {
        UserId me = user();
        List<UserId> others = List.of(user(), user(), user());
        others.forEach(other -> friendships.create(UserPair.of(me, other), NOW));

        List<Friend> first = friendships.of(me, Optional.empty(), 2);
        List<Friend> rest = friendships.of(me, Optional.of(first.getLast().user()), 2);

        assertThat(first).hasSize(2);
        assertThat(rest).hasSize(1);
        assertThat(java.util.stream.Stream.concat(first.stream(), rest.stream()).map(Friend::user))
                .containsExactlyInAnyOrderElementsOf(others);
        assertThat(first.getFirst().since()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("FRD-03 : un blocage se voit dans les deux sens de la paire")
    void frd03BlockIsSeenFromBothSides() {
        UserId a = user();
        UserId b = user();
        UserId c = user();
        jdbc.sql("INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)")
                .param(b.value())
                .param(a.value())
                .update();

        assertThat(blocks.blockedEitherWay(UserPair.of(a, b))).isTrue();
        assertThat(blocks.blockedEitherWay(UserPair.of(b, a))).isTrue();
        assertThat(blocks.blockedEitherWay(UserPair.of(a, c))).isFalse();
    }
}
