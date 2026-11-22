package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;

/** ACC-02 : demande de changement du courriel d'un compte actif, en mode sudo ; un code part à la nouvelle adresse. */
public interface RequestEmailChangeUseCase {

    void request(UserId user, EmailAddress email);
}
