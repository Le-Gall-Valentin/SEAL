package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Écrit les événements de connexion dans {@code security_events}, chacun dans sa propre transaction. */
@Component
public class AuthenticationJournalAdapter implements SecurityEventPort {

    private final SecurityJournal journal;

    public AuthenticationJournalAdapter(SecurityJournal journal) {
        this.journal = journal;
    }

    @Override
    public void loginSucceeded(UserId user) {
        journal.recordStandalone(new SecurityEvent(SecurityEventType.LOGIN_SUCCEEDED, user, Map.of()));
    }

    /** SEC-21 : seul le compte, s'il existe, est noté ; la saisie ne l'est jamais (ADR 0021, mise à jour). */
    @Override
    public void loginFailed(@Nullable UserId user) {
        journal.recordStandalone(new SecurityEvent(SecurityEventType.LOGIN_FAILED, user, Map.of()));
    }

    @Override
    public void loggedOut(UserId user) {
        journal.recordStandalone(new SecurityEvent(SecurityEventType.LOGOUT, user, Map.of()));
    }
}
