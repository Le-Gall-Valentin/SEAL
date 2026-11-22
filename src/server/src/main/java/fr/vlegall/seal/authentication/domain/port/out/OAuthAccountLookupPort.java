package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.UserId;

/**
 * AUTH-31, AUTH-33, AUTH-55 : ce que le contexte {@code authentication} demande au contexte {@code identity} sur un
 * compte (ADR 0008) : la connexion OAuth, la ré-authentification et la lecture des méthodes de connexion (ADR 0035).
 */
public interface OAuthAccountLookupPort {

    /** AUTH-16, AUTH-31, SEC-03 : l'état du compte, lu une seule fois. */
    AccountLoginState state(UserId user);

    /** AUTH-33 : vrai si un compte actif possède ce courriel ; rien d'autre n'en est livré. */
    boolean activeAccountOwns(String email);
}
