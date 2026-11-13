package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Coordonnées d'un compte actif, demandées au contexte {@code identity} pour la réinitialisation (ADR 0008, 0022). */
public interface AccountContactPort {

    /** AUTH-70 : coordonnées du compte actif dont le courriel vérifié est la saisie ; vide sinon (ADR 0022). */
    Optional<AccountContact> findActiveByEmail(String email);

    /** AUTH-70 : coordonnées du compte s'il est encore actif ; vide sinon. */
    Optional<AccountContact> findActiveContact(UserId user);
}
