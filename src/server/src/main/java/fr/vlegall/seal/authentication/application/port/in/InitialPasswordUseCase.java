package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Premier mot de passe d'un compte, à l'inscription (AUTH-11, AUTH-13), en deux temps : le hachage hors de toute
 * transaction, puis l'écriture dans celle de l'appelant. Argon2id et l'attente de son sémaphore ne retiennent ainsi
 * aucune connexion à la base (ADR 0019, point 3). Appelé depuis un autre contexte : la signature ne porte que des types
 * de la JDK et {@link UserId} (ADR 0008).
 */
public interface InitialPasswordUseCase {

    /**
     * Vérifie le mot de passe, puis le hache. Refusé dans une transaction.
     *
     * @param personalWords identifiant et partie locale du courriel, qui ne comptent pas (ADR 0019)
     * @return le haché, préfixé par son algorithme ; vide si le mot de passe enfreint AUTH-11
     */
    Optional<String> prepare(String password, List<String> personalWords);

    /** Enregistre un haché rendu par {@link #prepare}, dans la transaction de l'appelant, obligatoire. */
    void store(UserId user, String preparedHash);
}
