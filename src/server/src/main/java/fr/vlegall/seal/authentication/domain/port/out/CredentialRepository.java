package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Table {@code user_credentials} : un compte uniquement OAuth n'y a pas de ligne (ADR 0009). */
public interface CredentialRepository {

    void save(UserId user, String passwordHash);

    /** AUTH-20 : haché du mot de passe du compte ; vide pour un compte sans mot de passe (OAuth, lot 2). */
    Optional<String> findHash(UserId user);

    /**
     * AUTH-70 : remplace le haché d'un compte qui a déjà un mot de passe, dans la transaction de l'appelant.
     *
     * @return faux si le compte n'a pas de mot de passe : rien n'est écrit (ADR 0022, point 3)
     */
    boolean changeHash(UserId user, String passwordHash);
}
