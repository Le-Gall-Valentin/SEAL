package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.UserId;

/** AUTH-31, AUTH-33 : ce que la connexion OAuth demande au contexte {@code identity} sur un compte (ADR 0008). */
public interface OAuthAccountLookupPort {

    /** AUTH-16, AUTH-31 : l'état du compte lié à une identité OAuth, lu une seule fois. */
    AccountLoginState state(UserId user);

    /** AUTH-33 : vrai si un compte actif possède ce courriel ; rien d'autre n'en est livré. */
    boolean activeAccountOwns(String email);
}
