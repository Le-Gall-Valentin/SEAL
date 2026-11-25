package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationType;
import fr.vlegall.seal.conversations.domain.model.DirectKey;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import fr.vlegall.seal.conversations.domain.model.TestConversations;
import fr.vlegall.seal.conversations.domain.port.out.ConversationActivityPort;
import fr.vlegall.seal.conversations.domain.port.out.ConversationNotificationPort;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import fr.vlegall.seal.conversations.domain.port.out.FriendshipPort;
import fr.vlegall.seal.conversations.domain.port.out.MemberDirectoryPort;
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

/** Doublures en mémoire des ports du contexte {@code conversations}, partagées par les tests de handlers. */
final class InMemoryConversations {

    static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    /** Ordre des {@code uuid} de PostgreSQL ({@link UserId#POSTGRES_ORDER}), appliqué aux identifiants de conversation. */
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UserId::new, UserId.POSTGRES_ORDER);

    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final Map<ConversationId, Conversation> byId = new LinkedHashMap<>();
    private final Map<DirectKey, ConversationId> directs = new HashMap<>();
    private final Set<Set<UserId>> friendships = new HashSet<>();
    private final Map<UserId, MemberProfile> profiles = new HashMap<>();
    private long next = 1;

    /** CNV-04 : une annonce {@code conv.update}, avec les comptes prévenus. */
    record Notified(ConversationId conversation, Set<UserId> users) {}

    final List<Notified> notified = new ArrayList<>();

    ConversationNotificationPort notifications() {
        return (conversation, users) -> notified.add(new Notified(conversation, Set.copyOf(users)));
    }

    UserId person(String username) {
        UserId id = new UserId(UUID.randomUUID());
        profiles.put(id, new MemberProfile(id, username, "Nom " + username));
        return id;
    }

    void befriend(UserId a, UserId b) {
        friendships.add(Set.of(a, b));
    }

    void unfriend(UserId a, UserId b) {
        friendships.remove(Set.of(a, b));
    }

    void deactivate(UserId user) {
        profiles.remove(user);
    }

    ConversationRepository repository() {
        return new ConversationRepository() {
            @Override
            public ConversationId nextId() {
                return new ConversationId(new UUID(0L, next++));
            }

            @Override
            public boolean createDirect(DirectKey key, UserId a, UserId b, Instant at) {
                if (directs.containsKey(key)) {
                    return false;
                }
                ConversationId id = nextId();
                byId.put(id, TestConversations.direct(id, a, b, at));
                directs.put(key, id);
                return true;
            }

            @Override
            public Optional<Conversation> findDirect(DirectKey key) {
                return Optional.ofNullable(directs.get(key)).map(byId::get);
            }

            @Override
            public void createGroup(Conversation group) {
                byId.put(group.id(), group);
            }

            @Override
            public Optional<Conversation> findForMember(ConversationId id, UserId member) {
                return Optional.ofNullable(byId.get(id)).filter(c -> c.isActiveMember(member));
            }

            @Override
            public Optional<Conversation> lockForMember(ConversationId id, UserId member) {
                return findForMember(id, member);
            }

            @Override
            public void save(Conversation before, Conversation after, Instant at) {
                byId.put(after.id(), after);
            }

            @Override
            public List<Conversation> pageForMember(UserId member, Optional<ConversationId> after, int limit) {
                return byId.values().stream()
                        .filter(c -> c.isActiveMember(member))
                        .filter(c -> after.map(a -> UUID_ORDER.compare(c.id().value(), a.value()) > 0)
                                .orElse(true))
                        .sorted(Comparator.comparing(c -> c.id().value(), UUID_ORDER))
                        .limit(limit)
                        .toList();
            }

            @Override
            public boolean shareActiveGroup(UserId a, UserId b) {
                return byId.values().stream()
                        .anyMatch(
                                c -> c.type() == ConversationType.GROUP && c.isActiveMember(a) && c.isActiveMember(b));
            }
        };
    }

    FriendshipPort friendshipPort() {
        return new FriendshipPort() {
            @Override
            public boolean areAllFriends(UserId user, Collection<UserId> others) {
                return others.stream()
                        .allMatch(other -> !user.equals(other) && friendships.contains(Set.of(user, other)));
            }
        };
    }

    MemberDirectoryPort directory() {
        return users ->
                users.stream().map(profiles::get).filter(Objects::nonNull).toList();
    }

    ConversationActivityPort activity() {
        return (member, conversations) -> Map.of();
    }

    ActiveFriends activeFriends() {
        return new ActiveFriends(friendshipPort(), directory());
    }

    ConversationSummaries summaries() {
        return new ConversationSummaries(directory(), activity());
    }
}
