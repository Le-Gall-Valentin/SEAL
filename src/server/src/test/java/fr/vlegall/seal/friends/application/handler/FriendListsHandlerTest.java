package fr.vlegall.seal.friends.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.FriendSummary;
import fr.vlegall.seal.friends.domain.model.InteractionStats;
import fr.vlegall.seal.friends.domain.model.Invitation;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.UserPair;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-10, ADR 0037 (point 3) : pages des listes, comptes inactifs retirés après la lecture. */
@DisplayName("FRD-10 : listes d'amis et d'invitations")
class FriendListsHandlerTest {

    private final InMemoryFriends friends = new InMemoryFriends();
    private final Person me = friends.person("moi");

    private ListFriendsHandler friendsList() {
        return new ListFriendsHandler(friends.friendships(), friends.directory(), friends.interactions());
    }

    private ListFriendRequestsHandler requestsList() {
        return new ListFriendRequestsHandler(friends.requests(), friends.directory());
    }

    @Test
    @DisplayName("ADR 0037, point 3 : un ami inactif en milieu de page est retiré, la suite part de la page brute")
    void adr0037InactiveFriendMidPageKeepsTheRawCursor() {
        Person first = friends.person("premier");
        Person gone = friends.person("parti");
        Person last = friends.person("dernier");
        for (Person friend : new Person[] {first, gone, last}) {
            friends.friendships.put(UserPair.of(me.user(), friend.user()), InMemoryFriends.NOW);
        }
        friends.active.remove(gone.user());

        Page<FriendSummary> page = friendsList().list(me.user(), Optional.empty(), 2);
        Page<FriendSummary> rest = friendsList().list(me.user(), page.next().map(UserId::new), 2);

        assertThat(page.items()).extracting(summary -> summary.person().user()).containsExactly(first.user());
        assertThat(page.next()).contains(gone.user().value());
        assertThat(rest.items()).extracting(summary -> summary.person().user()).containsExactly(last.user());
        assertThat(rest.next()).isEmpty();
    }

    @Test
    @DisplayName("FRD-10 : statistiques connues reprises, aucune interaction sinon")
    void frd10StatisticsDefaultToNone() {
        Person talker = friends.person("bavard");
        Person quiet = friends.person("discret");
        friends.friendships.put(UserPair.of(me.user(), talker.user()), InMemoryFriends.NOW);
        friends.friendships.put(UserPair.of(me.user(), quiet.user()), InMemoryFriends.NOW);
        InteractionStats known = new InteractionStats(Optional.of(Instant.parse("2026-11-20T08:00:00Z")), 7);
        friends.stats.put(talker.user(), known);

        Page<FriendSummary> page = friendsList().list(me.user(), Optional.empty(), 50);

        assertThat(page.items()).extracting(FriendSummary::stats).containsExactly(known, InteractionStats.NONE);
    }

    @Test
    @DisplayName("ADR 0037, point 3 : invitations reçues, émetteur inactif retiré, curseur de la page brute")
    void adr0037IncomingRequestsSkipInactiveSenders() {
        Person active = friends.person("actif");
        Person gone = friends.person("absent");
        Person later = friends.person("ensuite");
        FriendRequest first = friends.pending(active, me);
        FriendRequest hidden = friends.pending(gone, me);
        FriendRequest third = friends.pending(later, me);
        friends.pending(me, friends.person("ailleurs"));
        friends.active.remove(gone.user());

        Page<Invitation> page = requestsList().list(me.user(), Direction.INCOMING, Optional.empty(), 2);
        Page<Invitation> rest = requestsList().list(me.user(), Direction.INCOMING, page.next(), 2);

        assertThat(page.items()).extracting(Invitation::id).containsExactly(first.id());
        assertThat(page.items().getFirst().other()).isEqualTo(active);
        assertThat(page.next()).contains(hidden.id());
        assertThat(rest.items()).extracting(Invitation::id).containsExactly(third.id());
    }
}
