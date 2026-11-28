package fr.vlegall.seal.security.session;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.stereotype.Component;

/**
 * TRN-12, AUTH-55, ADR 0044 : traduit chaque renommage de session, publié par {@link SessionMigration} (seule à
 * renommer, ADR 0045), en {@link SessionRenewedEvent}.
 */
@Component
public class SessionRenewals {

    private final ApplicationEventPublisher events;

    public SessionRenewals(ApplicationEventPublisher events) {
        this.events = events;
    }

    @EventListener
    public void renamed(SessionFixationProtectionEvent event) {
        events.publishEvent(new SessionRenewedEvent(event.getOldSessionId(), event.getNewSessionId()));
    }
}
