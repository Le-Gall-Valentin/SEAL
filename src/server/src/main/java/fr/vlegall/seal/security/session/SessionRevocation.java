package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * AUTH-50 : révoque les sessions d'un compte, retrouvées en base par le nom du principal, l'identifiant du compte
 * ({@link SealPrincipal#getName()}).
 *
 * <p>Spring Session n'écrit un nouvel identifiant de session qu'à la fin de la requête : appeler {@link #revokeOthers}
 * avant de changer l'identifiant de la session courante, sinon sa ligne, qui porte encore l'ancien, serait supprimée.
 * Une révocation qui fait tomber au moins une session est journalisée (SEC-20), hors de toute transaction. Comme pour
 * un dépassement de limite (ADR 0013, point 3), un journal indisponible n'annule rien : les sessions sont déjà
 * supprimées, et l'échec est écrit en erreur (A10).
 */
@Component
public final class SessionRevocation {

    private static final Logger LOG = LoggerFactory.getLogger(SessionRevocation.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final SecurityJournal journal;

    public SessionRevocation(FindByIndexNameSessionRepository<? extends Session> sessions, SecurityJournal journal) {
        this.sessions = sessions;
        this.journal = journal;
    }

    /** Toutes les sessions du compte sauf {@code currentSessionId} (AUTH-16, ADR 0021 point 4). */
    public void revokeOthers(UserId user, String currentSessionId) {
        revoke(
                user,
                sessions.findByPrincipalName(user.value().toString()).keySet().stream()
                        .filter(id -> !id.equals(currentSessionId))
                        .toList());
    }

    /** Toutes les sessions du compte (AUTH-70). */
    public void revokeAll(UserId user) {
        revoke(
                user,
                List.copyOf(
                        sessions.findByPrincipalName(user.value().toString()).keySet()));
    }

    private void revoke(UserId user, List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        ids.forEach(sessions::deleteById);
        try {
            journal.recordStandalone(new SecurityEvent(
                    SecurityEventType.SESSION_REVOKED, user, Map.of("sessions", Integer.toString(ids.size()))));
        } catch (RuntimeException e) {
            LOG.error("Journal de sécurité indisponible : révocation de sessions non enregistrée", e);
        }
    }
}
