package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.ResetOutcome;

/** Nouveau mot de passe par lien (AUTH-70, AUTH-71, ADR 0022 point 6). */
public interface ResetPasswordUseCase {

    /** Remplace le mot de passe et révoque toutes les sessions du compte ; refusé dans une transaction. */
    ResetOutcome reset(String token, String password);
}
