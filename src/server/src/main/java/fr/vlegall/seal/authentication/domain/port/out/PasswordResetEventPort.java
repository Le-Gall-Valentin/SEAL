package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** Journal de la réinitialisation du mot de passe (SEC-20, AUTH-70). */
@FunctionalInterface
public interface PasswordResetEventPort {

    /** AUTH-70 : mot de passe réinitialisé par lien. */
    void passwordReset(UserId user);
}
