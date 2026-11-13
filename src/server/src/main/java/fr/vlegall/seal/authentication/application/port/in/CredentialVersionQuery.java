package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.OptionalLong;

/** Version courante du mot de passe d'un compte, que chaque session ouverte par mot de passe compare (ADR 0023). */
public interface CredentialVersionQuery {

    /** Version du mot de passe ; vide pour un compte sans mot de passe. */
    OptionalLong currentVersion(UserId user);
}
