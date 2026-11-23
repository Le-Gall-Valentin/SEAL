package fr.vlegall.seal.friends.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FRD-01 : invitation")
class FriendRequestTest {

    private final UserId sender = new UserId(UUID.randomUUID());
    private final UserId receiver = new UserId(UUID.randomUUID());
    private final FriendRequest request = new FriendRequest(
            UUID.randomUUID(), sender, receiver, RequestStatus.PENDING, Instant.parse("2026-11-23T12:00:00Z"));

    @Test
    @DisplayName("FRD-01 : l'invitation connaît son émetteur, son destinataire et leur paire")
    void frd01RequestKnowsItsEnds() {
        assertThat(request.sentBy(sender)).isTrue();
        assertThat(request.sentBy(receiver)).isFalse();
        assertThat(request.sentTo(receiver)).isTrue();
        assertThat(request.sentTo(sender)).isFalse();
        assertThat(request.pair()).isEqualTo(UserPair.of(receiver, sender));
        assertThat(request.other(sender)).isEqualTo(receiver);
    }

    @Test
    @DisplayName("FRD-01 : une invitation à soi-même n'existe pas (CHECK sender_id <> receiver_id)")
    void frd01NoRequestToOneself() {
        assertThatThrownBy(() ->
                        new FriendRequest(UUID.randomUUID(), sender, sender, RequestStatus.PENDING, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
