package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.PendingOAuthRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AUTH-32 : l'inscription OAuth en attente, gardée dans la session non authentifiée (Spring Session JDBC) pendant 10
 * minutes. Une attente expirée est retirée à la première lecture.
 */
@Component
public final class PendingOAuthSession {

    /** Nom de l'attribut de session. */
    static final String ATTRIBUTE = "fr.vlegall.seal.oauth.pending";

    private final Clock clock;

    public PendingOAuthSession(Clock clock) {
        this.clock = clock;
    }

    /** Remplace toute attente précédente de la session. */
    public void remember(HttpServletRequest request, ExternalIdentity identity) {
        request.getSession(true)
                .setAttribute(ATTRIBUTE, PendingOAuthRegistration.startingAt(identity, clock.instant()));
    }

    /** L'attente vivante de la session ; vide sans session, sans attente ou après expiration. */
    public Optional<PendingOAuthRegistration> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        if (session.getAttribute(ATTRIBUTE) instanceof PendingOAuthRegistration pending
                && pending.isAlive(clock.instant())) {
            return Optional.of(pending);
        }
        session.removeAttribute(ATTRIBUTE);
        return Optional.empty();
    }

    /** Retire l'attente : elle ne sert qu'une fois. */
    public void forget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }
}
