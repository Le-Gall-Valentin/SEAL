package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;

/**
 * Refus métier du contexte {@code identity}, convertis en Problem Details par le gestionnaire commun (ADR 0010). Le
 * message ne porte que le code : aucune saisie n'y est recopiée.
 */
public abstract sealed class IdentityException extends SealException
        permits IdentityException.UsernameInvalid,
                IdentityException.UsernameReserved,
                IdentityException.UsernameTaken,
                IdentityException.InvalidCode,
                IdentityException.CodeExpired {

    private static final long serialVersionUID = 1L;

    private IdentityException(ErrorCode code) {
        super(code);
    }

    /** AUTH-01. */
    public static final class UsernameInvalid extends IdentityException {
        private static final long serialVersionUID = 1L;

        public UsernameInvalid() {
            super(ErrorCode.USERNAME_INVALID);
        }
    }

    /** AUTH-02. */
    public static final class UsernameReserved extends IdentityException {
        private static final long serialVersionUID = 1L;

        public UsernameReserved() {
            super(ErrorCode.USERNAME_RESERVED);
        }
    }

    /** AUTH-02 et AUTH-03 : pris, même par un compte en attente ou supprimé. */
    public static final class UsernameTaken extends IdentityException {
        private static final long serialVersionUID = 1L;

        public UsernameTaken() {
            super(ErrorCode.USERNAME_TAKEN);
        }
    }

    /** AUTH-14 : code erroné, essai compté. */
    public static final class InvalidCode extends IdentityException {
        private static final long serialVersionUID = 1L;

        public InvalidCode() {
            super(ErrorCode.INVALID_CODE);
        }
    }

    /** AUTH-14 : aucun code vivant (ADR 0019, point 4). */
    public static final class CodeExpired extends IdentityException {
        private static final long serialVersionUID = 1L;

        public CodeExpired() {
            super(ErrorCode.CODE_EXPIRED);
        }
    }
}
