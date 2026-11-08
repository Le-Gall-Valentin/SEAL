package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.Username;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Table {@code users}. */
public interface UserRepository {

    /**
     * Crée un compte en attente ; la base garantit l'unicité de l'identifiant (AUTH-02, AUTH-03).
     *
     * @throws IdentityException.UsernameTaken si l'identifiant existe déjà, quel que soit le statut de son compte
     */
    UserId createPending(Username username, DisplayName displayName, EmailAddress email);

    Optional<Account> findById(UserId id);

    /** AUTH-15 : vrai si un compte actif possède cette adresse, sans tenir compte de la casse. */
    boolean activeAccountOwns(EmailAddress email);

    /** Vrai si l'identifiant est pris, par un compte de n'importe quel statut (AUTH-03). */
    boolean exists(Username username);

    /** Ceux de ces identifiants qui sont déjà pris, en une seule requête. */
    Set<String> takenAmong(Collection<Username> candidates);
}
