package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetEventPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** Écritures de la réinitialisation, dans une seule transaction (AUTH-70, ADR 0022 point 6). */
@ApplicationService
public class PasswordResetWriter {

    private final PasswordResetTokenRepository tokens;
    private final CredentialRepository credentials;
    private final PasswordResetEventPort journal;

    public PasswordResetWriter(
            PasswordResetTokenRepository tokens, CredentialRepository credentials, PasswordResetEventPort journal) {
        this.tokens = tokens;
        this.credentials = credentials;
        this.journal = journal;
    }

    /**
     * Verrouille le compte, comme l'émission d'un lien (ADR 0022, point 4), puis consomme le lien de façon atomique :
     * de deux appels simultanés, un seul écrit. Remplace ensuite le haché, invalide les autres liens du compte et
     * journalise {@code PASSWORD_RESET} dans la même transaction (ADR 0013, point 6) : un journal indisponible annule
     * tout, et le lien reste utilisable.
     *
     * @return faux si le compte n'a plus de mot de passe ou si le lien n'est plus valable : aucun haché n'est écrit
     */
    @Transactional
    public boolean apply(LiveReset reset, String preparedHash, Instant now) {
        if (!credentials.lock(reset.user())
                || !tokens.consume(reset.id(), now)
                || !credentials.changeHash(reset.user(), preparedHash)) {
            return false;
        }
        tokens.consumeAll(reset.user(), now);
        journal.passwordReset(reset.user());
        return true;
    }
}
