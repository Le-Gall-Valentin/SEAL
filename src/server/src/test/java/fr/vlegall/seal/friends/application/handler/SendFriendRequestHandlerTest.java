package fr.vlegall.seal.friends.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.InvitationOutcome;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-01, FRD-03 : ordre des contrôles de l'invitation, vu par les ports qu'il interroge. */
@DisplayName("FRD-03 : contrôles de l'invitation")
class SendFriendRequestHandlerTest {

    private static final UserId SENDER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000001"));
    private static final UserId RECEIVER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private final List<String> calls = new ArrayList<>();
    private boolean blocked;
    private boolean open = true;

    private SendFriendRequestHandler handler() {
        DirectoryPort directory = new DirectoryPort() {
            @Override
            public Optional<Person> findActiveByUsername(String username) {
                return Optional.of(new Person(RECEIVER, username, "Sarah"));
            }

            @Override
            public List<Person> activeProfiles(Collection<UserId> users) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean acceptsInvitations(UserId user) {
                calls.add("policy");
                return open;
            }
        };
        FriendshipRepository friendships = new FriendshipRepository() {
            @Override
            public boolean exists(UserPair pair) {
                calls.add("friends");
                return false;
            }

            @Override
            public boolean create(UserPair pair, Instant since) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean dissolve(UserPair pair) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Friend> of(UserId user, Optional<UserId> after, int limit) {
                throw new UnsupportedOperationException();
            }
        };
        FriendRequestRepository requests = new FriendRequestRepository() {
            @Override
            public void lockPair(UserPair pair) {
                calls.add("lock");
            }

            @Override
            public Optional<FriendRequest> pendingBetween(UserPair pair) {
                calls.add("pending");
                return Optional.empty();
            }

            @Override
            public Optional<FriendRequest> createPending(UserId sender, UserId receiver, Instant createdAt) {
                calls.add("create");
                return Optional.of(new FriendRequest(UUID.randomUUID(), sender, receiver, RequestStatus.PENDING, NOW));
            }

            @Override
            public Optional<FriendRequest> lockPending(UUID id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close(UUID id, RequestStatus status, Instant respondedAt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FriendRequest> pending(UserId user, Direction direction, Optional<UUID> after, int limit) {
                throw new UnsupportedOperationException();
            }
        };
        FriendsEventPort events = new FriendsEventPort() {
            @Override
            public void requestSent(UserId sender, UserId receiver, UUID requestId) {
                calls.add("journal");
            }

            @Override
            public void requestAccepted(UserId receiver, UserId sender, UUID requestId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void friendRemoved(UserId user, UserId formerFriend) {
                throw new UnsupportedOperationException();
            }
        };
        return new SendFriendRequestHandler(
                directory,
                pair -> {
                    calls.add("blocks");
                    return blocked;
                },
                friendships,
                requests,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("FRD-03 : un blocage consulte aussi la politique, pour ne pas se distinguer d'elle par le temps")
    void frd03BlockAndPolicyAreBothConsulted() {
        blocked = true;

        assertThat(handler().send(SENDER, "sarah")).isInstanceOf(InvitationOutcome.CannotInvite.class);
        assertThat(calls).contains("blocks", "policy").doesNotContain("friends", "pending", "create");
    }

    @Test
    @DisplayName("FRD-03 : invitations fermées : même chemin que le blocage")
    void frd03ClosedInvitationsTakeTheSamePath() {
        open = false;

        assertThat(handler().send(SENDER, "sarah")).isInstanceOf(InvitationOutcome.CannotInvite.class);
        assertThat(calls).contains("blocks", "policy").doesNotContain("friends", "pending", "create");
    }

    @Test
    @DisplayName("FRD-01 : la paire est verrouillée avant de lire amitié et invitations, jusqu'à la création")
    void frd01PairIsLockedBeforeItsChecks() {
        assertThat(handler().send(SENDER, "sarah")).isInstanceOf(InvitationOutcome.Sent.class);
        assertThat(calls).containsSubsequence("lock", "friends", "pending", "create", "journal");
        assertThat(calls.indexOf("lock")).isLessThan(calls.indexOf("friends"));
    }
}
