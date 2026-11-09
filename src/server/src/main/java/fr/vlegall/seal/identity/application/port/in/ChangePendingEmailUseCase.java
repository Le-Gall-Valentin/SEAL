package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;

/** AUTH-17 : changement du courriel d'un compte en attente ; un nouveau code part à la nouvelle adresse. */
public interface ChangePendingEmailUseCase {

    void change(UserId user, EmailAddress email);
}
