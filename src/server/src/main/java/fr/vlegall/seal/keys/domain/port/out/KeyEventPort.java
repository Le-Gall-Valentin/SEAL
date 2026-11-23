package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import java.util.Optional;

/**
 * SEC-20 : publications de clés, écrites dans la transaction de la publication ; A09 : signatures refusées (ADR 0025,
 * point 10).
 */
public interface KeyEventPort {

    void identityPublished(UserId user, byte[] serial);

    /**
     * KEY-34 : identité réinitialisée (rotation de clé), avec le numéro de série du nouveau certificat et celui du
     * certificat courant qu'il remplace, s'il y en avait un.
     */
    void identityReset(UserId user, byte[] serial, Optional<byte[]> revokedSerial);

    void subkeyPublished(UserId user, byte[] ekId);

    /**
     * Preuve de possession ou liaison de sous-clé refusée ({@code INVALID_SIGNATURE}) : signalée avec le compte et la
     * structure, sans aucune valeur reçue.
     */
    void signatureRejected(UserId user, SignedStructure structure);
}
