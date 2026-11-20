package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.model.PendingOAuthRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AUTH-32 : l'inscription OAuth en attente, gardée dans la session non authentifiée (Spring Session JDBC) pendant 10
 * minutes. La session ne garde qu'un instantané de types simples, propre à l'infrastructure : le domaine ne dépend pas du
 * format de la session (ADR 0008), et l'identité est revalidée à la relecture. Une attente expirée est retirée à la
 * première lecture ; une lecture sans attente n'écrit rien.
 */
@Component
public final class PendingOAuthSession {

    /** Nom de l'attribut de session. */
    static final String ATTRIBUTE = "fr.vlegall.seal.oauth.pending";

    private final Clock clock;

    public PendingOAuthSession(Clock clock) {
        this.clock = clock;
    }

    /** Remplace toute attente précédente de la session ; l'attente part de maintenant, le retour du fournisseur. */
    public void remember(HttpServletRequest request, ExternalIdentity identity) {
        request.getSession(true).setAttribute(ATTRIBUTE, Snapshot.of(identity, clock.instant()));
    }

    /** L'attente vivante de la session ; vide sans session, sans attente, ou après expiration. */
    public Optional<PendingOAuthRegistration> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object stored = session.getAttribute(ATTRIBUTE);
        if (stored == null) {
            return Optional.empty();
        }
        Optional<PendingOAuthRegistration> pending = stored instanceof Snapshot snapshot
                ? snapshot.toPending().filter(found -> found.isAlive(clock.instant()))
                : Optional.empty();
        if (pending.isEmpty()) {
            session.removeAttribute(ATTRIBUTE);
        }
        return pending;
    }

    /** Retire l'attente : elle ne sert qu'une fois. */
    public void forget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(ATTRIBUTE) != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    /** Ce que la session garde de l'attente : des types de la JDK seulement. */
    record Snapshot(
            String provider,
            String subject,
            String email,
            boolean emailVerified,
            String displayName,
            Instant authenticatedAt)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        static Snapshot of(ExternalIdentity identity, Instant authenticatedAt) {
            return new Snapshot(
                    identity.provider().name(),
                    identity.subject(),
                    identity.email(),
                    identity.emailVerified(),
                    identity.displayName(),
                    authenticatedAt);
        }

        /** L'attente, si l'instantané décrit encore une identité valide ; vide sinon (échec fermé). */
        Optional<PendingOAuthRegistration> toPending() {
            if (provider == null
                    || subject == null
                    || email == null
                    || displayName == null
                    || authenticatedAt == null) {
                return Optional.empty();
            }
            try {
                ExternalIdentity identity = new ExternalIdentity(
                        OAuthProvider.valueOf(provider), subject, email, emailVerified, displayName);
                return Optional.of(new PendingOAuthRegistration(identity, authenticatedAt));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        /** SEC-21 : le courriel n'apparaît pas dans les journaux. */
        @Override
        public String toString() {
            return "Snapshot[provider=" + provider + ", subject=" + subject + ", email=***]";
        }
    }
}
