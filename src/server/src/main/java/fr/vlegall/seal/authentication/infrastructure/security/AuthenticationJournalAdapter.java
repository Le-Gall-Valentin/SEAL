package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthLinkEventPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordChangeEventPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetEventPort;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Écrit les événements de connexion, OAuth compris, et de réinitialisation dans {@code security_events} : la connexion
 * et la déconnexion chacune dans sa propre transaction (ADR 0021), la réinitialisation et la liaison OAuth dans celle
 * qui les écrit (ADR 0013, point 6).
 */
@Component
public class AuthenticationJournalAdapter
        implements SecurityEventPort,
                PasswordResetEventPort,
                PasswordChangeEventPort,
                OAuthEventPort,
                OAuthLinkEventPort {

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

    /** SEC-20 : dans la transaction qui crée la liaison (ADR 0013, point 6). */
    @Override
    public void oauthLinked(UserId user, OAuthProvider provider) {
        journal.recordInTransaction(
                new SecurityEvent(SecurityEventType.OAUTH_LINKED, user, Map.of("provider", provider.registrationId())));
    }

    @Override
    public void oauthLoginSucceeded(UserId user, OAuthProvider provider) {
        journal.recordStandalone(new SecurityEvent(
                SecurityEventType.LOGIN_SUCCEEDED,
                user,
                Map.of("method", "oauth", "provider", provider.registrationId())));
    }

    /** SEC-21 : le fournisseur et une cause courte, jamais le courriel, le code ni un jeton. */
    @Override
    public void oauthLoginFailed(@Nullable UserId user, @Nullable OAuthProvider provider, String cause) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", "oauth");
        if (provider != null) {
            details.put("provider", provider.registrationId());
        }
        details.put("cause", cause);
        journal.recordStandalone(new SecurityEvent(SecurityEventType.LOGIN_FAILED, user, details));
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

    /** SEC-20, SEC-21 : dans la transaction qui remplace le haché ; seul le compte est noté (ACC-01). */
    @Override
    public void passwordChanged(UserId user) {
        journal.recordInTransaction(new SecurityEvent(SecurityEventType.PASSWORD_CHANGED, user, Map.of()));
    }
}
