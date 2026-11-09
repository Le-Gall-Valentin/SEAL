package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** Table {@code user_credentials} : un compte uniquement OAuth n'y a pas de ligne (ADR 0009). */
@FunctionalInterface
public interface CredentialRepository {

    void save(UserId user, String passwordHash);
}
