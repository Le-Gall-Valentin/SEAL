package fr.vlegall.seal.friends.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.infrastructure.web.dto.SendFriendRequestBody;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD-02, 15.2 : seules les invitations créées comptent dans les 20, même quand l'invitation échoue sur une exception. */
@DisplayName("FRD-02 : jeton de création des invitations")
class FriendRequestsControllerTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-0000000000aa"));

    private final RateLimiter limiter =
            new RateLimiter(MutableClock.fixedAt(Instant.parse("2026-11-23T12:00:00Z")), mock(SecurityJournal.class));

    @Test
    @DisplayName("FRD-02 : une exception pendant l'invitation rend le jeton de création, pas celui de la tentative")
    void frd02ExceptionRefundsTheCreationToken() {
        FriendRequestsController controller = new FriendRequestsController(
                (sender, input) -> {
                    throw new IllegalStateException("panne simulée");
                },
                (user, id) -> true,
                (user, id) -> true,
                (user, id) -> true,
                (user, direction, after, limit) -> new Page<>(java.util.List.of(), Optional.empty()),
                limiter);

        assertThatThrownBy(() -> controller.send(USER, new SendFriendRequestBody("sarah")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(limiter.remaining(RateLimit.INVITATIONS_CREATED_PER_USER, USER))
                .isEqualTo(20);
        assertThat(limiter.remaining(RateLimit.INVITATION_ATTEMPTS_PER_USER, USER))
                .isEqualTo(59);
    }
}
