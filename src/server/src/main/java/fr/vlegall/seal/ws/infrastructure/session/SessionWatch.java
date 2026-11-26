package fr.vlegall.seal.ws.infrastructure.session;

import fr.vlegall.seal.security.session.SessionRenewedEvent;
import fr.vlegall.seal.security.session.SessionsEndedEvent;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.Connection;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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

    private static final Logger LOG = LoggerFactory.getLogger(SessionWatch.class);

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
        event.sessionIds().forEach(id -> registry.bySession(id).forEach(c -> close(c, CloseReason.SESSION_ENDED)));
    }

    /**
     * AUTH-23, ADR 0044 : la session change d'identifiant ; ses connexions le suivent. La ronde juge ensuite le compte
     * que garde la session : une session passée à un autre compte ferme ses connexions.
     */
    @EventListener
    public void renewed(SessionRenewedEvent event) {
        Instant now = clock.instant();
        registry.bySession(event.previousId()).forEach(connection -> connection.follow(event.currentId(), now));
    }

    /** La ronde, planifiée par {@link SessionSweepScheduling} (les tests d'intégration la lancent eux-mêmes). */
    public void sweep() {
        Instant now = clock.instant();
        for (Connection connection : registry.all()) {
            try {
                if (!sessions.alive(connection)) {
                    close(connection, CloseReason.SESSION_ENDED);
                } else if (!now.isBefore(connection.lastHeardAt().plus(SILENCE))) {
                    close(connection, CloseReason.SILENT);
                }
            } catch (RuntimeException failed) {
                // A10 : une connexion en échec n'arrête pas la ronde ; elle sera revue au tour suivant.
                LOG.warn(
                        "Ronde des WebSockets : connexion non vérifiée ({})",
                        failed.getClass().getSimpleName());
            }
        }
    }

    /**
     * ADR 0044 (décision 10) : la fermeture est demandée aussitôt, sur ce fil, et n'attend pas le client ; dès cet
     * instant, la connexion ne reçoit plus aucune trame. Un échec reste propre à sa connexion.
     */
    private void close(Connection connection, CloseReason reason) {
        try {
            connection.close(reason);
        } catch (RuntimeException failed) {
            LOG.debug(
                    "Fermeture d'une WebSocket en échec ({})", failed.getClass().getSimpleName());
        }
    }
}
