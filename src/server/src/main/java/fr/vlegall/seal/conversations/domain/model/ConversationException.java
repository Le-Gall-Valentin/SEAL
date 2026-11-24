package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;

/**
 * Refus métier du contexte {@code conversations} (ADR 0039, points 2 et 3), convertis en Problem Details par le
 * gestionnaire commun (ADR 0010). Le message ne porte que le code : aucune saisie n'y est recopiée.
 */
public abstract sealed class ConversationException extends SealException
        permits ConversationException.NotFound,
                ConversationException.Forbidden,
                ConversationException.NotFriends,
                ConversationException.NotAMember,
                ConversationException.GroupFull,
                ConversationException.InvalidName {

    private static final long serialVersionUID = 1L;

    private ConversationException(ErrorCode code, String... fields) {
        super(code, fields);
    }

    /** CNV-06 : conversation inconnue, ou appelant qui n'en est pas membre actif ; 404 plutôt que 403. */
    public static final class NotFound extends ConversationException {
        private static final long serialVersionUID = 1L;

        public NotFound() {
            super(ErrorCode.NOT_FOUND);
        }
    }

    /** GRP-03 : action réservée à l'administrateur, ou action de groupe sur une directe. */
    public static final class Forbidden extends ConversationException {
        private static final long serialVersionUID = 1L;

        public Forbidden() {
            super(ErrorCode.FORBIDDEN);
        }
    }

    /** CNV-01, GRP-02, GRP-03, FRD-21 : un identifiant n'est pas un ami actif, sans dire pourquoi. */
    public static final class NotFriends extends ConversationException {
        private static final long serialVersionUID = 1L;

        public NotFriends() {
            super(ErrorCode.NOT_FRIENDS);
        }
    }

    /** ADR 0039 (point 3) : le compte visé n'est pas membre actif. */
    public static final class NotAMember extends ConversationException {
        private static final long serialVersionUID = 1L;

        public NotAMember() {
            super(ErrorCode.NOT_A_MEMBER);
        }
    }

    /** GRP-01 : plus de 50 membres actifs. */
    public static final class GroupFull extends ConversationException {
        private static final long serialVersionUID = 1L;

        public GroupFull() {
            super(ErrorCode.GROUP_FULL);
        }
    }

    /** GRP-01, SEC-01 : nom de groupe hors de la règle. */
    public static final class InvalidName extends ConversationException {
        private static final long serialVersionUID = 1L;

        public InvalidName() {
            super(ErrorCode.VALIDATION_FAILED, "name");
        }
    }
}
