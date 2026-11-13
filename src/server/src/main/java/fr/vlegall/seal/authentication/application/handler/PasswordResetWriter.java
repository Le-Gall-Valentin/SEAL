package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** Écritures de la réinitialisation, dans une seule transaction (AUTH-70, ADR 0022 point 6). */
@ApplicationService
public class PasswordResetWriter {

    private final PasswordResetTokenRepository tokens;
    private final CredentialRepository credentials;

    public PasswordResetWriter(PasswordResetTokenRepository tokens, CredentialRepository credentials) {
        this.tokens = tokens;
        this.credentials = credentials;
    }

    /**
     * Consomme le lien, de façon atomique, avant tout : de deux appels simultanés, un seul écrit. Remplace ensuite le
     * haché, puis invalide les autres liens du compte.
     *
     * @return faux si le lien n'est plus valable ou si le compte n'a plus de mot de passe : aucun haché n'est écrit
     */
    @Transactional
    public boolean apply(LiveReset reset, String preparedHash, Instant now) {
        if (!tokens.consume(reset.id(), now) || !credentials.changeHash(reset.user(), preparedHash)) {
            return false;
        }
        tokens.consumeAll(reset.user(), now);
        return true;
    }
}
