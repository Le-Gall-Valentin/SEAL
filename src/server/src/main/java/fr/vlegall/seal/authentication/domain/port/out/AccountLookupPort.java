package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Compte d'une connexion ou d'une réinitialisation, demandé au contexte {@code identity} (ADR 0008). */
public interface AccountLookupPort {

    /** Identifiant d'un compte non supprimé, ou courriel d'un compte actif ; vide sinon (ADR 0019, ADR 0021). */
    Optional<UserId> findForLogin(String login);

    /** Vrai si le compte est actif, donc son courriel vérifié (AUTH-16). */
    boolean isVerified(UserId user);

    /** AUTH-70 : coordonnées du compte actif dont le courriel vérifié est la saisie ; vide sinon (ADR 0022). */
    Optional<AccountContact> findActiveByEmail(String email);

    /** AUTH-70 : coordonnées du compte s'il est encore actif ; vide sinon. */
    Optional<AccountContact> findActiveContact(UserId user);
}
