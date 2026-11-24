package fr.vlegall.seal.friends.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.UserPair;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-11, API-02 : retrait d'un ami et question « sont-ils amis ? ». */
@DisplayName("FRD-11 : amitiés")
class FriendshipHandlersTest {

    private final InMemoryFriends friends = new InMemoryFriends();
    private final Person me = friends.person("moi");
    private final Person friend = friends.person("ami");

    @Test
    @DisplayName("FRD-11 : retirer un ami supprime l'amitié et journalise ; un non-ami ou soi-même, rien")
    void frd11UnfriendRemovesOnlyAnExistingFriendship() {
        RemoveFriendHandler handler = new RemoveFriendHandler(friends.friendships(), friends.events());
        friends.friendships.put(UserPair.of(me.user(), friend.user()), InMemoryFriends.NOW);

        assertThat(handler.unfriend(me.user(), me.user())).isFalse();
        assertThat(handler.unfriend(me.user(), friends.person("inconnu").user()))
                .isFalse();
        assertThat(friends.events).isEmpty();

        assertThat(handler.unfriend(friend.user(), me.user())).isTrue();
        assertThat(friends.friendships).isEmpty();
        assertThat(friends.events).containsExactly("removed");
    }

    @Test
    @DisplayName("API-02 : deux amis le sont dans les deux sens ; un compte n'est pas son propre ami")
    void api02AreFriendsIsSymmetricAndNeverReflexive() {
        FriendshipQueryHandler handler = new FriendshipQueryHandler(friends.friendships());
        friends.friendships.put(UserPair.of(me.user(), friend.user()), InMemoryFriends.NOW);

        assertThat(handler.areFriends(me.user(), friend.user())).isTrue();
        assertThat(handler.areFriends(friend.user(), me.user())).isTrue();
        assertThat(handler.areFriends(me.user(), me.user())).isFalse();
        assertThat(handler.areFriends(me.user(), friends.person("autre").user()))
                .isFalse();
    }

    @Test
    @DisplayName("FRD-21, GRP-02 : tous amis d'un compte, en une question ; un tiers ou le compte lui-même, non")
    void frd21AllFriendsInOneQuestion() {
        FriendshipQueryHandler handler = new FriendshipQueryHandler(friends.friendships());
        var second = friends.person("second");
        friends.friendships.put(UserPair.of(me.user(), friend.user()), InMemoryFriends.NOW);
        friends.friendships.put(UserPair.of(second.user(), me.user()), InMemoryFriends.NOW);

        assertThat(handler.areAllFriends(me.user(), List.of(friend.user(), second.user())))
                .isTrue();
        assertThat(handler.areAllFriends(
                        me.user(),
                        List.of(friend.user(), friends.person("tiers").user())))
                .isFalse();
        assertThat(handler.areAllFriends(me.user(), List.of(friend.user(), me.user())))
                .isFalse();
    }
}
