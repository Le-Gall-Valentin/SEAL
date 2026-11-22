package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.InvalidateResetLinksUseCase;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.time.Clock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR 0034 : un changement de courriel (ACC-02) rend inutiles les liens de réinitialisation envoyés à l'ancienne
 * adresse. {@code MANDATORY} : l'invalidation suit le sort de la transaction qui change l'adresse.
 */
@ApplicationService
public class InvalidateResetLinksHandler implements InvalidateResetLinksUseCase {

    private final CredentialRepository credentials;
    private final PasswordResetTokenRepository tokens;
    private final Clock clock;

    public InvalidateResetLinksHandler(
            CredentialRepository credentials, PasswordResetTokenRepository tokens, Clock clock) {
        this.credentials = credentials;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidate(UserId user) {
        // Le verrou du compte sérialise cette invalidation avec l'émission d'un lien (ADR 0022, point 4) : un lien
        // demandé pendant le changement d'adresse est soit émis avant, puis invalidé ici, soit refusé par la demande,
        // qui relit l'adresse après ce même verrou. Un compte sans mot de passe n'a ni verrou ni lien.
        credentials.lock(user);
        tokens.consumeAll(user, clock.instant());
    }
}
