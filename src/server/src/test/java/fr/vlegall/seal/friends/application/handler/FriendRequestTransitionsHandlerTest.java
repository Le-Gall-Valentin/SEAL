package fr.vlegall.seal.friends.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-01, SEC-03, section 10.1 : accepter, refuser et révoquer, vus par les ports. */
@DisplayName("FRD-01 : transitions d'une invitation")
class FriendRequestTransitionsHandlerTest {

    private final InMemoryFriends friends = new InMemoryFriends();
    private final Person sender = friends.person("emetteur");
    private final Person receiver = friends.person("destinataire");
    private final FriendRequest request = friends.pending(sender, receiver);

    private AcceptFriendRequestHandler accept() {
        return new AcceptFriendRequestHandler(
                friends.open(),
                friends.requests(),
                friends.friendships(),
                friends.events(),
                friends.notifications(),
                InMemoryFriends.CLOCK);
    }

    private DeclineFriendRequestHandler decline() {
        return new DeclineFriendRequestHandler(
                friends.open(), friends.requests(), friends.notifications(), InMemoryFriends.CLOCK);
    }

    private RevokeFriendRequestHandler revoke() {
        return new RevokeFriendRequestHandler(
                friends.open(), friends.requests(), friends.notifications(), InMemoryFriends.CLOCK);
    }

    private RequestStatus status() {
        return friends.requests.get(request.id()).status();
    }

    @Test
    @DisplayName("FRD-01 : accepter verrouille la ligne puis la paire, clôt, crée l'amitié et journalise")
    void frd01AcceptLocksRowThenPairAndCreatesTheFriendship() {
        assertThat(accept().accept(receiver.user(), request.id())).isTrue();

        assertThat(friends.calls).containsSubsequence("lockPending", "lockPair");
        assertThat(friends.calls.indexOf("lockPending")).isLessThan(friends.calls.indexOf("lockPair"));
        assertThat(status()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(friends.friendships).containsKey(UserPair.of(sender.user(), receiver.user()));
        assertThat(friends.events).containsExactly("accepted");
        assertThat(friends.notified)
                .containsExactly("relation:" + receiver.user().value() + ":"
                        + sender.user().value());
    }

    @Test
    @DisplayName(
            "FRD-04, CDC 10.1, ADR 0044 : un refus n'est annoncé qu'à qui refuse, une révocation aux deux ; un échec, à personne")
    void frd04DeclineToTheReceiverOnlyRevokeToBoth() {
        FriendRequest other = friends.pending(receiver, sender);

        assertThat(decline().decline(sender.user(), request.id())).isFalse();
        assertThat(friends.notified).isEmpty();
        assertThat(decline().decline(receiver.user(), request.id())).isTrue();
        assertThat(revoke().revoke(receiver.user(), other.id())).isTrue();

        String pair = receiver.user().value() + ":" + sender.user().value();
        assertThat(friends.notified).containsExactly("declined:" + pair, "relation:" + pair);
    }

    @Test
    @DisplayName("SEC-03 : l'émetteur ne peut ni accepter ni refuser ; refusé avant le verrou de paire")
    void sec03SenderCannotAnswerItsOwnRequest() {
        assertThat(accept().accept(sender.user(), request.id())).isFalse();
        assertThat(decline().decline(sender.user(), request.id())).isFalse();

        assertThat(friends.calls).doesNotContain("lockPair");
        assertThat(status()).isEqualTo(RequestStatus.PENDING);
        assertThat(friends.friendships).isEmpty();
        assertThat(friends.events).isEmpty();
        assertThat(friends.notified).isEmpty();
    }

    @Test
    @DisplayName("ADR 0037 : l'autre compte n'est plus actif, l'invitation ne s'accepte plus")
    void adr0037InactiveSenderCannotBeAccepted() {
        friends.active.remove(sender.user());

        assertThat(accept().accept(receiver.user(), request.id())).isFalse();
        assertThat(status()).isEqualTo(RequestStatus.PENDING);
        assertThat(friends.friendships).isEmpty();
    }

    @Test
    @DisplayName("FRD-01, section 10.1 : refuser clôt sans amitié ni journal")
    void frd01DeclineClosesWithoutFriendship() {
        assertThat(decline().decline(receiver.user(), request.id())).isTrue();

        assertThat(status()).isEqualTo(RequestStatus.DECLINED);
        assertThat(friends.friendships).isEmpty();
        assertThat(friends.events).isEmpty();
        assertThat(friends.notified)
                .containsExactly("declined:" + receiver.user().value() + ":"
                        + sender.user().value());
    }

    @Test
    @DisplayName("FRD-01, section 10.1 : seul l'émetteur révoque ; une invitation close ou inconnue ne se révoque pas")
    void frd01OnlyTheSenderRevokes() {
        assertThat(revoke().revoke(receiver.user(), request.id())).isFalse();
        assertThat(revoke().revoke(sender.user(), request.id())).isTrue();
        assertThat(status()).isEqualTo(RequestStatus.REVOKED);

        assertThat(revoke().revoke(sender.user(), request.id())).isFalse();
        assertThat(revoke().revoke(sender.user(), UUID.randomUUID())).isFalse();
    }
}
