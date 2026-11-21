package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailChangeConfirmation;
import fr.vlegall.seal.identity.domain.model.VerificationCode;

/** ACC-02 : confirmation du changement de courriel par le code reçu à la nouvelle adresse. */
public interface ConfirmEmailChangeUseCase {

    EmailChangeConfirmation confirm(UserId user, VerificationCode code);
}
