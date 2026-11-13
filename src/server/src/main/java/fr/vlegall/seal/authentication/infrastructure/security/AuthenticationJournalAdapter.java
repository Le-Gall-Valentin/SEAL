package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.port.out.PasswordResetEventPort;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Écrit les événements de connexion et de réinitialisation dans {@code security_events} : la connexion et la
 * déconnexion chacune dans sa propre transaction (ADR 0021), la réinitialisation dans celle qui remplace le haché (ADR
 * 0013, point 6).
 */
@Component
public class AuthenticationJournalAdapter implements SecurityEventPort, PasswordResetEventPort {

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

    /**
     * SEC-20, SEC-21 : dans la transaction qui remplace le haché (ADR 0013, point 6) ; seul le compte est noté, ni le
     * jeton, ni le lien, ni le mot de passe.
     */
    @Override
    public void passwordReset(UserId user) {
        journal.recordInTransaction(new SecurityEvent(SecurityEventType.PASSWORD_RESET, user, Map.of()));
    }
}
