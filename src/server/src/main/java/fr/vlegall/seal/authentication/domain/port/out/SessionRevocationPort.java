package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * Révocation des sessions d'un compte (AUTH-50, AUTH-70, ACC-01). À appeler après la validation de l'écriture qui la
 * motive : appelée dans une transaction, elle supprimerait les sessions sans que l'écriture soit acquise.
 */
public interface SessionRevocationPort {

    /** Révoque toutes les sessions du compte, journalisé par {@code SESSION_REVOKED}. */
    void revokeAll(UserId user);

    /**
     * ACC-01 : révoque toutes les sessions du compte sauf {@code keptSession}, journalisé par {@code SESSION_REVOKED}.
     * À appeler avant que la session gardée change d'identifiant.
     */
    void revokeOthers(UserId user, String keptSession);
}
