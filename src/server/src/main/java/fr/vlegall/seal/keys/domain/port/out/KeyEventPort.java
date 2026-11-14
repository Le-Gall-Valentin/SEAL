package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * SEC-20 : publications de clés, écrites dans la transaction de la publication ; A09 : signatures refusées (ADR 0025,
 * point 10).
 */
public interface KeyEventPort {

    void identityPublished(UserId user, byte[] serial);

    void subkeyPublished(UserId user, byte[] ekId);

    /** Preuve de possession ou liaison de sous-clé refusée ({@code INVALID_SIGNATURE}) : signalée sans aucune valeur. */
    void signatureRejected();
}
