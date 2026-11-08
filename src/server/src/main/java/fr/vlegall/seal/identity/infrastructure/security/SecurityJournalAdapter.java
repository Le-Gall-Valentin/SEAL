package fr.vlegall.seal.identity.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.logging.LogValues;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Événements d'{@code identity} dans {@code security_events}, écrits dans la transaction du geste (ADR 0013). */
@Component
public class SecurityJournalAdapter implements SecurityEventPort {

    private final SecurityJournal journal;

    public SecurityJournalAdapter(SecurityJournal journal) {
        this.journal = journal;
    }

    @Override
    public void accountRegistered(UserId user, EmailAddress email) {
        record(SecurityEventType.ACCOUNT_REGISTERED, user, email);
    }

    @Override
    public void emailChanged(UserId user, EmailAddress newEmail) {
        record(SecurityEventType.EMAIL_CHANGED, user, newEmail);
    }

    /** SEC-21 : le courriel est masqué ({@code v***@exemple.ca}). */
    private void record(SecurityEventType type, UserId user, EmailAddress email) {
        journal.recordInTransaction(new SecurityEvent(type, user, Map.of("email", LogValues.maskEmail(email.value()))));
    }
}
