package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Table {@code user_credentials} : un compte uniquement OAuth n'y a pas de ligne (ADR 0009). */
public interface CredentialRepository {

    void save(UserId user, String passwordHash);

    /** AUTH-20 : haché du mot de passe du compte ; vide pour un compte sans mot de passe (OAuth, lot 2). */
    Optional<String> findHash(UserId user);
}
