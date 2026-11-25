package fr.vlegall.seal.ws.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SessionRenewedEvent;
import fr.vlegall.seal.security.session.SessionsEndedEvent;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import fr.vlegall.seal.ws.domain.model.FakeConnection;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSessionRepository;

class SessionWatchTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000001"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000002"));

    private final MutableClock clock = MutableClock.followingSystem();
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final SessionWatch watch = new SessionWatch(
            registry,
            new BoundSessions(new MapSessionRepository(new ConcurrentHashMap<>()), clock, Duration.ofHours(12)),
            clock);

    private FakeConnection open(UserId user, String sessionId) {
        FakeConnection connection = new FakeConnection(user, sessionId, clock.instant());
        assertThat(registry.open(connection)).isTrue();
        return connection;
    }

    @Test
    @DisplayName("AUTH-23, TRN-12 : un renouvellement pour le même compte fait suivre la connexion, sans la fermer")
    void auth23RenewalForTheSameAccountIsFollowed() {
        FakeConnection connection = open(USER, "avant");

        watch.renewed(new SessionRenewedEvent("avant", "apres", USER));

        assertThat(connection.sessionId()).isEqualTo("apres");
        assertThat(connection.formerSessionId()).contains("avant");
        assertThat(connection.closed()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-23, TRN-12 : un renouvellement pour un autre compte ferme la connexion en 4401")
    void auth23RenewalForAnotherAccountEndsTheConnection() {
        FakeConnection connection = open(USER, "avant");

        watch.renewed(new SessionRenewedEvent("avant", "apres", OTHER));

        assertThat(connection.closed()).contains(CloseReason.SESSION_ENDED);
        assertThat(connection.sessionId()).isEqualTo("avant");
    }

    @Test
    @DisplayName("TRN-12 : la fin d'une session ferme ses seules connexions")
    void trn12EndedSessionClosesOnlyItsConnections() {
        FakeConnection ended = open(USER, "finie");
        FakeConnection kept = open(USER, "gardee");

        watch.ended(new SessionsEndedEvent(Set.of("finie")));

        assertThat(ended.closed()).contains(CloseReason.SESSION_ENDED);
        assertThat(kept.closed()).isEmpty();
    }
}
