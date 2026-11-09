package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailVerification;
import fr.vlegall.seal.identity.domain.model.VerificationCode;

/** AUTH-14, AUTH-16 : vérification du courriel par le code. */
public interface VerifyEmailUseCase {

    /** Le refus est une valeur : la transaction est validée, l'essai manqué reste compté. */
    EmailVerification verify(UserId user, VerificationCode code);
}
