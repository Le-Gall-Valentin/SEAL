package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;

/**
 * Refus métier de la connexion OAuth, convertis en Problem Details par le gestionnaire commun (ADR 0010). Le message ne
 * porte que le code : ni courriel ni identifiant du fournisseur.
 */
public abstract sealed class OAuthException extends SealException permits OAuthException.AlreadyLinked {

    private static final long serialVersionUID = 1L;

    private OAuthException(ErrorCode code) {
        super(code);
    }

    /** AUTH-31 : ce compte du fournisseur est déjà lié, ou ce compte SEAL l'est déjà à ce fournisseur. */
    public static final class AlreadyLinked extends OAuthException {
        private static final long serialVersionUID = 1L;

        public AlreadyLinked() {
            super(ErrorCode.OAUTH_ALREADY_LINKED);
        }
    }
}
