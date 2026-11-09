package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;

/** Inscription classique (AUTH-10 à AUTH-15). */
public interface RegisterUseCase {

    /**
     * Crée le compte en attente, son mot de passe et son code, puis programme le courriel du code ou, si un compte actif
     * possède l'adresse, l'avertissement à son titulaire (AUTH-15). Tout ou rien.
     */
    UserId register(RegisterCommand command);
}
