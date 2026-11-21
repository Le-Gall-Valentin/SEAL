package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.Username;
import java.time.Instant;
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

    /**
     * AUTH-32 : compte d'une inscription OAuth dont le fournisseur garantit le courriel, actif d'emblée.
     *
     * @throws IdentityException.UsernameTaken si l'identifiant est pris entre-temps
     * @throws IdentityException.EmailInUse si un compte actif possède déjà ce courriel (index users_verified_email)
     */
    UserId createActive(Username username, DisplayName displayName, EmailAddress email, Instant verifiedAt);

    Optional<Account> findById(UserId id);

    /** Lit le compte en verrouillant sa ligne jusqu'à la fin de la transaction : les gestes concurrents attendent. */
    Optional<Account> lockById(UserId id);

    /**
     * AUTH-16 : courriel vérifié, compte actif.
     *
     * @throws IdentityException.CodeExpired si un autre compte actif vient d'obtenir l'adresse (ADR 0019, point 1)
     */
    void activate(UserId id, Instant verifiedAt);

    /** AUTH-17 : nouveau courriel d'un compte en attente. */
    void changeEmail(UserId id, EmailAddress email);

    /**
     * ACC-02 : nouveau courriel, vérifié, d'un compte actif.
     *
     * @throws IdentityException.CodeExpired si un autre compte actif vient d'obtenir l'adresse (index
     *     users_verified_email)
     */
    void changeActiveEmail(UserId id, EmailAddress email, Instant verifiedAt);

    /** ACC-03 : nouveau nom affiché d'un compte actif. */
    void changeDisplayName(UserId id, DisplayName displayName);

    /** AUTH-15 : vrai si un compte actif possède cette adresse, sans tenir compte de la casse. */
    boolean activeAccountOwns(EmailAddress email);

    /** AUTH-20 : compte non supprimé qui porte cet identifiant, en attente ou actif. */
    Optional<UserId> findNotDeletedByUsername(Username username);

    /** AUTH-20 : compte actif qui possède cette adresse, sans tenir compte de la casse (ADR 0019). */
    Optional<UserId> findActiveByEmail(EmailAddress email);

    /** Vrai si l'identifiant est pris, par un compte de n'importe quel statut (AUTH-03). */
    boolean exists(Username username);

    /** Ceux de ces identifiants qui sont déjà pris, en une seule requête. */
    Set<String> takenAmong(Collection<Username> candidates);
}
