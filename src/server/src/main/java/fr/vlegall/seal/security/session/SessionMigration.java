package fr.vlegall.seal.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.stereotype.Component;

/**
 * AUTH-23, ADR 0045 : tout renommage de session est une migration. L'ancienne session est supprimée et ses attributs
 * passent dans une nouvelle, sous un nouveau {@code PRIMARY_ID} : une requête concurrente qui écrit l'ancienne après
 * coup ne la fait pas revivre. La date de création de la première session est reportée ({@link SessionOrigin}, AUTH-52).
 *
 * <p>Échec fermé, par {@link SessionAuthenticationException} :
 *
 * <ul>
 *   <li>une session lue en base qui n'y est plus (déconnexion ou révocation entre-temps, AUTH-53) n'est pas migrée, donc
 *       pas ranimée : la migration en supprime elle-même la ligne et compte ce qu'elle a supprimé ;
 *   <li>la nouvelle session est toujours neuve : une session existante, retrouvée par un second cookie, n'est jamais
 *       adoptée.
 * </ul>
 *
 * <p>Seul {@link SessionEstablisher} s'en sert ; les filtres OAuth ne renomment pas (ADR 0045, point 2). Chaque migration
 * d'une authentification publie {@code SessionFixationProtectionEvent}, que {@link SessionRenewals} traduit (TRN-12,
 * ADR 0044).
 */
@Component
public final class SessionMigration extends SessionFixationProtectionStrategy {

    private final StoredSessions stored;
    private final ApplicationEventPublisher events;

    public SessionMigration(ApplicationEventPublisher events, StoredSessions stored) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.events = Objects.requireNonNull(events, "events");
        setMigrateSessionAttributes(true);
        setApplicationEventPublisher(events);
    }

    @Override
    public void onAuthentication(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        claimStoredRow(request);
        super.onAuthentication(authentication, request, response);
    }

    @Override
    protected Map<String, Object> extractAttributes(HttpSession session) {
        Map<String, Object> attributes = super.extractAttributes(session);
        attributes.put(SessionOrigin.ATTRIBUTE, SessionOrigin.createdAt(session));
        return attributes;
    }

    @Override
    protected void onSessionChange(String originalSessionId, HttpSession newSession, Authentication auth) {
        rejectAdopted(originalSessionId, newSession);
        super.onSessionChange(originalSessionId, newSession, auth);
    }

    /**
     * AUTH-32 : migration sans authentification, pour l'inscription OAuth en attente. Rien n'est publié : l'appelant
     * ferme lui-même les WebSockets de l'ancienne session. Sans session, une session neuve est créée.
     */
    public void migrate(HttpServletRequest request) {
        HttpSession previous = request.getSession(false);
        if (previous == null) {
            Objects.requireNonNull(request.getSession(true), "session");
            return;
        }
        claimStoredRow(request);
        Map<String, Object> attributes = extractAttributes(previous);
        int maxInactiveInterval = previous.getMaxInactiveInterval();
        String previousId = previous.getId();
        previous.invalidate();
        HttpSession renewed = request.getSession(true);
        rejectAdopted(previousId, renewed);
        attributes.forEach(renewed::setAttribute);
        renewed.setMaxInactiveInterval(maxInactiveInterval);
    }

    /**
     * AUTH-53 : supprime la ligne de la session lue en base au début de la requête. Si une autre requête l'a supprimée
     * entre-temps, la session est invalidée et rien n'est migré.
     */
    private void claimStoredRow(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.isNew() || !request.isRequestedSessionIdValid()) {
            // Session créée par cette requête : aucune ligne n'existe encore.
            return;
        }
        String id = session.getId();
        if (!stored.delete(id)) {
            session.invalidate();
            // TRN-12 : ses WebSockets se ferment aussitôt, si la requête qui l'a supprimée ne l'a pas annoncé.
            events.publishEvent(new SessionsEndedEvent(Set.of(id)));
            throw new SessionAuthenticationException("session supprimée pendant la requête");
        }
    }

    /**
     * Scénario 16.1 : une session déjà existante n'est jamais adoptée comme nouvelle session. L'ancienne, déjà supprimée,
     * et l'adoptée, invalidée, ferment leurs WebSockets (TRN-12).
     */
    private void rejectAdopted(String originalSessionId, HttpSession renewed) {
        if (!renewed.isNew()) {
            String adopted = renewed.getId();
            renewed.invalidate();
            events.publishEvent(new SessionsEndedEvent(Set.of(originalSessionId, adopted)));
            throw new SessionAuthenticationException("session existante adoptée par la migration");
        }
    }
}
