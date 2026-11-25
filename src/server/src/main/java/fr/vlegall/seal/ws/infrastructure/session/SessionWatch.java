package fr.vlegall.seal.ws.infrastructure.session;

import fr.vlegall.seal.security.session.SessionRenewedEvent;
import fr.vlegall.seal.security.session.SessionsEndedEvent;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.Connection;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TRN-12, TRN-13, ADR 0043 (point 5) : chaque connexion suit sa session HTTP. Une déconnexion ou une révocation la
 * ferme aussitôt ; une expiration, une session disparue autrement ou 60 secondes sans trame, à la ronde suivante,
 * toutes les 10 secondes.
 */
@Component
public class SessionWatch {

    /** TRN-13 : 60 secondes sans trame ferment la connexion (4408). */
    static final Duration SILENCE = Duration.ofSeconds(60);

    private final ConnectionRegistry registry;
    private final BoundSessions sessions;
    private final Clock clock;

    public SessionWatch(ConnectionRegistry registry, BoundSessions sessions, Clock clock) {
        this.registry = registry;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** AUTH-53, TRN-12 : déconnexion ou révocation. */
    @EventListener
    public void ended(SessionsEndedEvent event) {
        event.sessionIds().forEach(id -> registry.bySession(id).forEach(c -> c.close(CloseReason.SESSION_ENDED)));
    }

    /** AUTH-23 : la session change d'identifiant ; la connexion la suit, sauf si la session passe à un autre compte. */
    @EventListener
    public void renewed(SessionRenewedEvent event) {
        for (Connection connection : registry.bySession(event.previousId())) {
            if (connection.user().equals(event.user())) {
                connection.follow(event.currentId());
            } else {
                connection.close(CloseReason.SESSION_ENDED);
            }
        }
    }

    /** La ronde (les tests d'intégration la lancent eux-mêmes, à l'horloge réglable). */
    @Scheduled(fixedDelayString = "${seal.ws.sweep-delay:PT10S}")
    public void sweep() {
        Instant now = clock.instant();
        for (Connection connection : registry.all()) {
            if (!sessions.alive(connection)) {
                connection.close(CloseReason.SESSION_ENDED);
            } else if (!now.isBefore(connection.lastHeardAt().plus(SILENCE))) {
                connection.close(CloseReason.SILENT);
            }
        }
    }
}
