package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.InteractionStats;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.BlockRepository;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendNotificationPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import fr.vlegall.seal.friends.domain.port.out.InteractionStatsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Ports du contexte {@code friends} en mémoire, qui notent leurs appels, pour les tests des handlers. */
final class InMemoryFriends {

    static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Ordre des {@code uuid} de PostgreSQL, celui des paires appliqué aux identifiants d'invitation. */
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UserId::new, UserId.POSTGRES_ORDER);

    final List<String> calls = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    /** FRD-04 : annonces en direct, « request:destinataire » ou « relation:un:autre ». */
    final List<String> notified = new ArrayList<>();

    final Map<UUID, FriendRequest> requests = new LinkedHashMap<>();
    final Map<UserPair, Instant> friendships = new HashMap<>();
    final Set<UserPair> blocks = new HashSet<>();
    final Map<UserId, Person> active = new HashMap<>();
    final Set<UserId> closedInvitations = new HashSet<>();
    final Map<UserId, InteractionStats> stats = new HashMap<>();
    private long sequence;

    /** Compte actif, d'identifiant croissant à chaque appel. */
    Person person(String username) {
        Person person =
                new Person(new UserId(new UUID(0x0192_0000_0000_7000L, ++sequence)), username, "Nom " + username);
        active.put(person.user(), person);
        return person;
    }

    FriendRequest pending(Person sender, Person receiver) {
        FriendRequest request = new FriendRequest(
                new UUID(0x0193_0000_0000_7000L, ++sequence),
                sender.user(),
                receiver.user(),
                RequestStatus.PENDING,
                NOW);
        requests.put(request.id(), request);
        return request;
    }

    FriendRequestRepository requests() {
        return new FriendRequestRepository() {
            @Override
            public void lockPair(UserPair pair) {
                calls.add("lockPair");
            }

            @Override
            public Optional<FriendRequest> pendingBetween(UserPair pair) {
                return requests.values().stream()
                        .filter(r ->
                                r.status() == RequestStatus.PENDING && r.pair().equals(pair))
                        .findFirst();
            }

            @Override
            public Optional<FriendRequest> createPending(UserId sender, UserId receiver, Instant createdAt) {
                if (pendingBetween(UserPair.of(sender, receiver)).isPresent()) {
                    return Optional.empty();
                }
                FriendRequest request = new FriendRequest(
                        new UUID(0x0193_0000_0000_7000L, ++sequence),
                        sender,
                        receiver,
                        RequestStatus.PENDING,
                        createdAt);
                requests.put(request.id(), request);
                return Optional.of(request);
            }

            @Override
            public Optional<FriendRequest> lockPending(UUID id) {
                calls.add("lockPending");
                return Optional.ofNullable(requests.get(id)).filter(r -> r.status() == RequestStatus.PENDING);
            }

            @Override
            public void close(UUID id, RequestStatus status, Instant respondedAt) {
                FriendRequest request = lockPending(id).orElseThrow(IllegalStateException::new);
                requests.put(
                        id, new FriendRequest(id, request.sender(), request.receiver(), status, request.createdAt()));
            }

            @Override
            public List<FriendRequest> pending(UserId user, Direction direction, Optional<UUID> after, int limit) {
                return requests.values().stream()
                        .filter(r -> r.status() == RequestStatus.PENDING)
                        .filter(r -> direction == Direction.INCOMING ? r.sentTo(user) : r.sentBy(user))
                        .filter(r -> after.map(a -> UUID_ORDER.compare(r.id(), a) > 0)
                                .orElse(true))
                        .sorted(Comparator.comparing(FriendRequest::id, UUID_ORDER))
                        .limit(limit)
                        .toList();
            }
        };
    }

    FriendshipRepository friendships() {
        return new FriendshipRepository() {
            @Override
            public boolean exists(UserPair pair) {
                return friendships.containsKey(pair);
            }

            @Override
            public boolean create(UserPair pair, Instant since) {
                return friendships.putIfAbsent(pair, since) == null;
            }

            @Override
            public boolean dissolve(UserPair pair) {
                return friendships.remove(pair) != null;
            }

            @Override
            public int countAmong(UserId user, Collection<UserId> others) {
                return (int) others.stream()
                        .distinct()
                        .filter(other -> !other.equals(user) && friendships.containsKey(UserPair.of(user, other)))
                        .count();
            }

            @Override
            public List<Friend> of(UserId user, Optional<UserId> after, int limit) {
                return friendships.entrySet().stream()
                        .filter(e -> e.getKey().low().equals(user)
                                || e.getKey().high().equals(user))
                        .map(e -> new Friend(e.getKey().other(user), e.getValue()))
                        .filter(f -> after.map(a -> UserId.POSTGRES_ORDER.compare(f.user(), a) > 0)
                                .orElse(true))
                        .sorted(Comparator.comparing(Friend::user, UserId.POSTGRES_ORDER))
                        .limit(limit)
                        .toList();
            }
        };
    }

    BlockRepository blocks() {
        return blocks::contains;
    }

    DirectoryPort directory() {
        return new DirectoryPort() {
            @Override
            public Optional<Person> findActiveByUsername(String username) {
                return active.values().stream()
                        .filter(p -> p.username().equals(username))
                        .findFirst();
            }

            @Override
            public List<Person> activeProfiles(Collection<UserId> users) {
                return users.stream()
                        .distinct()
                        .map(active::get)
                        .filter(Objects::nonNull)
                        .toList();
            }

            @Override
            public boolean acceptsInvitations(UserId user) {
                return !closedInvitations.contains(user);
            }
        };
    }

    FriendsEventPort events() {
        return new FriendsEventPort() {
            @Override
            public void requestSent(UserId sender, UserId receiver, UUID requestId) {
                events.add("sent");
            }

            @Override
            public void requestAccepted(UserId receiver, UserId sender, UUID requestId) {
                events.add("accepted");
            }

            @Override
            public void friendRemoved(UserId user, UserId formerFriend) {
                events.add("removed");
            }
        };
    }

    FriendNotificationPort notifications() {
        return notifications(notified);
    }

    static FriendNotificationPort notifications(List<String> notified) {
        return new FriendNotificationPort() {
            @Override
            public void requestReceived(UserId receiver, UUID requestId) {
                notified.add("request:" + receiver.value());
            }

            @Override
            public void requestDeclined(UserId receiver, UserId sender) {
                notified.add("declined:" + receiver.value() + ":" + sender.value());
            }

            @Override
            public void relationChanged(UserId one, UserId other) {
                notified.add("relation:" + one.value() + ":" + other.value());
            }
        };
    }

    InteractionStatsPort interactions() {
        return (user, friends) -> {
            Map<UserId, InteractionStats> known = new HashMap<>(stats);
            known.keySet().retainAll(new HashSet<>(friends));
            return known;
        };
    }

    OpenRequests open() {
        return new OpenRequests(requests(), directory());
    }
}
