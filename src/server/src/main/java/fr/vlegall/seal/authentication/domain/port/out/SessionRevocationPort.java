package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * Révocation des sessions d'un compte (AUTH-50, AUTH-70). À appeler après la validation de l'écriture qui la motive :
 * appelée dans une transaction, elle supprimerait les sessions sans que l'écriture soit acquise.
 */
@FunctionalInterface
public interface SessionRevocationPort {

    /** Révoque toutes les sessions du compte, journalisé par {@code SESSION_REVOKED}. */
    void revokeAll(UserId user);
}
