package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Compte d'une connexion, demandé au contexte {@code identity} (ADR 0008). */
public interface AccountLookupPort {

    /** Identifiant d'un compte non supprimé, ou courriel d'un compte actif ; vide sinon (ADR 0019, ADR 0021). */
    Optional<UserId> findForLogin(String login);

    /** Vrai si le compte est actif, donc son courriel vérifié (AUTH-16). */
    boolean isVerified(UserId user);

    /** AUTH-16, AUTH-31 : l'état du compte lié à une identité OAuth, lu une seule fois. */
    AccountLoginState state(UserId user);

    /** AUTH-33 : vrai si un compte actif possède ce courriel ; rien d'autre n'en est livré. */
    boolean activeAccountOwns(String email);
}
