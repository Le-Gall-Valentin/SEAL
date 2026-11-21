package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** Journal du changement de mot de passe depuis les paramètres (SEC-20, ACC-01). */
@FunctionalInterface
public interface PasswordChangeEventPort {

    /** ACC-01 : mot de passe changé, dans la transaction qui remplace le haché. */
    void passwordChanged(UserId user);
}
