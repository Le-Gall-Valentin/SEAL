package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SignedStructure;

/**
 * SEC-20 : publications de clés, écrites dans la transaction de la publication ; A09 : signatures refusées (ADR 0025,
 * point 10).
 */
public interface KeyEventPort {

    void identityPublished(UserId user, byte[] serial);

    void subkeyPublished(UserId user, byte[] ekId);

    /**
     * Preuve de possession ou liaison de sous-clé refusée ({@code INVALID_SIGNATURE}) : signalée avec le compte et la
     * structure, sans aucune valeur reçue.
     */
    void signatureRejected(UserId user, SignedStructure structure);
}
