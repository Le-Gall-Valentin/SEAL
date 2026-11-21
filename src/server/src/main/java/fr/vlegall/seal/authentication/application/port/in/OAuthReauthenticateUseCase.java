package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.common.UserId;

/** AUTH-55, ADR 0034 (point 2) : ré-authentification d'une session complète par un nouvel aller-retour OAuth. */
public interface OAuthReauthenticateUseCase {

    /** @return vrai si l'identité revenue du fournisseur est celle déjà liée au compte ; un refus est journalisé */
    boolean reauthenticate(UserId user, ExternalIdentity identity);
}
