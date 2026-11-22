package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;

/** AUTH-55, ADR 0034 : ré-authentification d'une session complète par son mot de passe. */
public interface ReauthenticateUseCase {

    /** @return vrai si le mot de passe est celui du compte actif ; un refus est journalisé */
    boolean reauthenticate(UserId user, String password);
}
