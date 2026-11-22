package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordChangeEventPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordChangedMailPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Clock;
import java.util.OptionalLong;
import org.springframework.transaction.annotation.Transactional;

/** Écritures du changement de mot de passe (ACC-01), dans une seule transaction (ADR 0013, point 6 ; ADR 0034). */
@ApplicationService
public class PasswordChangeWriter {

    private final CredentialRepository credentials;
    private final PasswordResetTokenRepository tokens;
    private final PasswordChangeEventPort journal;
    private final PasswordChangedMailPort mail;
    private final Clock clock;

    public PasswordChangeWriter(
            CredentialRepository credentials,
            PasswordResetTokenRepository tokens,
            PasswordChangeEventPort journal,
            PasswordChangedMailPort mail,
            Clock clock) {
        this.credentials = credentials;
        this.tokens = tokens;
        this.journal = journal;
        this.mail = mail;
        this.clock = clock;
    }

    /**
     * Remplace le haché s'il est encore celui qui a été vérifié, puis invalide les liens de réinitialisation vivants,
     * journalise {@code PASSWORD_CHANGED} et programme le courriel du titulaire. Un journal indisponible annule tout.
     *
     * @return la version désormais en base ; vide si le mot de passe a changé entre-temps : rien n'est écrit
     */
    @Transactional
    public OptionalLong apply(AccountContact contact, String verifiedHash, String newHash, boolean newVersion) {
        OptionalLong version = credentials.changeHashIfUnchanged(contact.user(), verifiedHash, newHash, newVersion);
        if (version.isEmpty()) {
            return version;
        }
        // ADR 0034 : un lien envoyé pour l'ancien mot de passe ne doit plus servir.
        tokens.consumeAll(contact.user(), clock.instant());
        journal.passwordChanged(contact.user());
        mail.sendPasswordChanged(contact.email(), contact.username());
        return version;
    }
}
