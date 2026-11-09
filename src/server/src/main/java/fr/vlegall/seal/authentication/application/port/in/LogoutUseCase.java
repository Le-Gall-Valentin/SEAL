package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;

/** Déconnexion (AUTH-53) : journalise le geste ; la couche web ferme ensuite la session. */
public interface LogoutUseCase {

    void logout(UserId user);
}
