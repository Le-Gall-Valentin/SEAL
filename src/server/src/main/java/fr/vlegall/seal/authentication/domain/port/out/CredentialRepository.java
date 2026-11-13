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

    /**
     * AUTH-70 : verrouille la ligne du compte jusqu'à la fin de la transaction de l'appelant. L'émission d'un lien et la
     * réinitialisation le prennent en premier : elles se sérialisent par compte, sans interblocage (ADR 0022, point 4).
     *
     * @return faux si le compte n'a pas de mot de passe : rien n'est verrouillé
     */
    boolean lock(UserId user);
}
