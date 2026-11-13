package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import fr.vlegall.seal.authentication.application.port.in.InspectPasswordResetQuery;
import fr.vlegall.seal.authentication.application.port.in.RequestPasswordResetUseCase;
import fr.vlegall.seal.authentication.application.port.in.ResetPasswordUseCase;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PasswordResetRequest;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;

/** Ordre des limites et découplage de la réponse, que les tests d'intégration n'observent pas (ADR 0022). */
class PasswordResetControllerTest {

    private static final String IP = "203.0.113.7";

    /** Vrai limiteur, espionné : la prise des jetons reste observable. */
    private final RateLimiter limiter = spy(
            new RateLimiter(MutableClock.fixedAt(Instant.parse("2026-11-13T12:00:00Z")), mock(SecurityJournal.class)));

    private final List<String> requested = new ArrayList<>();
    private final RequestPasswordResetUseCase requests = requested::add;
    private final List<Runnable> scheduled = new ArrayList<>();
    private final PasswordResetController controller = new PasswordResetController(
            requests, mock(InspectPasswordResetQuery.class), mock(ResetPasswordUseCase.class), limiter, scheduled::add);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    PasswordResetControllerTest() {
        request.setRemoteAddr(IP);
    }

    @Test
    @DisplayName("SEC-10 : la limite par IP se prend avant celle par courriel saisi, normalisé (ADR 0013, point 4)")
    void sec10IpLimitIsConsumedBeforeTheEmailLimit() {
        controller.request(new PasswordResetRequest("Valentin@Exemple.ca"), request);

        InOrder order = inOrder(limiter);
        order.verify(limiter).consume(RateLimit.PASSWORD_RESETS_PER_IP, IP);
        order.verify(limiter).consume(RateLimit.PASSWORD_RESETS_PER_EMAIL, "valentin@exemple.ca");
    }

    @Test
    @DisplayName("AUTH-70 : la réponse part avant le traitement, programmé en arrière-plan (ADR 0022)")
    void auth70ResponseDoesNotWaitForTheRequestHandling() {
        controller.request(new PasswordResetRequest("valentin@exemple.ca"), request);

        assertThat(requested).as("rien n'est fait sur le fil de la requête").isEmpty();
        assertThat(scheduled).hasSize(1);
        scheduled.getFirst().run();
        assertThat(requested).containsExactly("valentin@exemple.ca");
    }

    @Test
    @DisplayName("SEC-10 : une demande refusée par une limite ne programme rien")
    void sec10RefusedRequestSchedulesNothing() {
        doThrow(new RateLimitExceededException(RateLimit.PASSWORD_RESETS_PER_IP, Duration.ofHours(1)))
                .when(limiter)
                .consume(RateLimit.PASSWORD_RESETS_PER_IP, IP);

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(() -> controller.request(new PasswordResetRequest("valentin@exemple.ca"), request));

        assertThat(scheduled).isEmpty();
        assertThat(requested).isEmpty();
    }
}
