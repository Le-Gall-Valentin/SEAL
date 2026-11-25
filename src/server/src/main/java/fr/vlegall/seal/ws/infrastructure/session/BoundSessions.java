package fr.vlegall.seal.ws.infrastructure.session;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.security.session.FullSessions;
import fr.vlegall.seal.ws.domain.model.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

/**
 * TRN-12, AUTH-52 : la session HTTP d'une connexion, lue à l'horloge de SEAL. Elle vit tant qu'elle existe, qu'elle
 * n'a dépassé ni 30 minutes d'inactivité ni 12 heures d'âge, et qu'elle garde la session complète du compte de la
 * connexion.
 */
@Component
public final class BoundSessions {

    /** AUTH-52, ADR 0043 (point 4) : une action prolonge la session au plus une fois par minute. */
    static final Duration EXTENSION_INTERVAL = Duration.ofMinutes(1);

    private final SessionRepository<? extends Session> sessions;
    private final Clock clock;
    private final Duration absoluteTimeout;

    BoundSessions(SessionRepository<? extends Session> sessions, Clock clock, Duration absoluteTimeout) {
        this.sessions = sessions;
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Autowired
    public BoundSessions(SessionRepository<? extends Session> sessions, Clock clock, SealProperties properties) {
        this(sessions, clock, properties.session().absoluteTimeout());
    }

    /** La session de la connexion vit encore. */
    public boolean alive(Connection connection) {
        Instant now = clock.instant();
        Session current = sessions.findById(connection.sessionId());
        if (current != null) {
            return alive(current, connection, now);
        }
        // AUTH-23 : renouvelée par une requête pas encore finie, la session garde son ancien identifiant.
        return connection
                .formerSessionId()
                .map(sessions::findById)
                .filter(former -> alive(former, connection, now))
                .isPresent();
    }

    /**
     * AUTH-52 : une trame née d'une action de l'utilisateur prolonge la session si son dernier accès date d'une minute
     * au moins. Faux si la session est finie : la trame n'est alors pas traitée.
     */
    public boolean userActed(Connection connection) {
        return touch(sessions, connection);
    }

    private <S extends Session> boolean touch(SessionRepository<S> repository, Connection connection) {
        Instant now = clock.instant();
        S current = repository.findById(connection.sessionId());
        if (current == null) {
            // Renouvellement en cours : rien n'est écrit sous l'ancien identifiant, que la requête va remplacer.
            return alive(connection);
        }
        if (!alive(current, connection, now)) {
            return false;
        }
        if (!current.getLastAccessedTime().plus(EXTENSION_INTERVAL).isAfter(now)) {
            current.setLastAccessedTime(now);
            repository.save(current);
        }
        return true;
    }

    private boolean alive(@Nullable Session session, Connection connection, Instant now) {
        return session != null
                && now.isBefore(session.getLastAccessedTime().plus(session.getMaxInactiveInterval()))
                && now.isBefore(session.getCreationTime().plus(absoluteTimeout))
                && FullSessions.storedUser(session)
                        .filter(connection.user()::equals)
                        .isPresent();
    }
}
