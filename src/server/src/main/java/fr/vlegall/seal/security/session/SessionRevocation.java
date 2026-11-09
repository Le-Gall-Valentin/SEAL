package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * AUTH-50 : révoque les sessions d'un compte, retrouvées en base par le nom du principal, l'identifiant du compte
 * ({@link SealPrincipal#getName()}).
 *
 * <p>Spring Session n'écrit un nouvel identifiant de session qu'à la fin de la requête : appeler {@link #revokeOthers}
 * avant de changer l'identifiant de la session courante, sinon sa ligne, qui porte encore l'ancien, serait supprimée.
 */
@Component
public final class SessionRevocation {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevocation(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Toutes les sessions du compte sauf {@code currentSessionId} (AUTH-16, ADR 0021 point 4). */
    public void revokeOthers(UserId user, String currentSessionId) {
        sessions.findByPrincipalName(user.value().toString()).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .forEach(sessions::deleteById);
    }

    /** Toutes les sessions du compte (AUTH-70). */
    public void revokeAll(UserId user) {
        sessions.findByPrincipalName(user.value().toString()).keySet().forEach(sessions::deleteById);
    }
}
