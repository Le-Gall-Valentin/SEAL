package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;

/** Déconnexion (AUTH-53) : journalise le geste, une fois la session fermée par la couche web. */
public interface LogoutUseCase {

    void logout(UserId user);
}
