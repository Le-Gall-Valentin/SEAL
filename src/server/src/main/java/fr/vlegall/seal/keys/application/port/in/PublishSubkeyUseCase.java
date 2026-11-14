package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SubkeyPublication;
import java.time.Instant;

/** KEY-05 : publication d'une sous-clé de chiffrement, liée au certificat courant par une signature d'IK. */
public interface PublishSubkeyUseCase {

    /** {@code kem} est déjà {@code DHKEM-X25519-HKDF-SHA256}, validé par la requête (ADR 0025, point 7). */
    SubkeyPublication publish(
            UserId user,
            byte[] certSerial,
            byte[] ekId,
            byte[] ekPublic,
            Instant createdAt,
            Instant expiresAt,
            byte[] signature);
}
