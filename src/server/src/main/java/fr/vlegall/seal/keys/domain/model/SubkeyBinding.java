package fr.vlegall.seal.keys.domain.model;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Canonical;
import fr.vlegall.seal.crypto.Ed25519;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Liaison d'une sous-clé de chiffrement X25519 à la clé d'identité (KEY-05, CDC section 6.3, PROTOCOL.md section
 * 4.4), signée par IK. Les règles d'acceptation par le serveur (ADR 0025, point 7) s'ajoutent à ce format.
 */
public record SubkeyBinding(
        UserId user,
        byte[] certSerial,
        byte[] ekId,
        String kem,
        byte[] ekPublic,
        Instant createdAt,
        Instant expiresAt) {

    /** Étiquette de la structure : elle fixe aussi l'algorithme de signature, Ed25519 (ADR 0025, point 11). */
    public static final String LABEL = "SEAL-subkey-v1";

    /** Seul mécanisme d'encapsulation accepté avant X-Wing (P2). */
    public static final String KEM = "DHKEM-X25519-HKDF-SHA256";

    /** {@code ekId} : 16 octets aléatoires. */
    public static final int EK_ID_BYTES = 16;

    /** Clé publique X25519 brute. */
    public static final int EK_PUBLIC_BYTES = 32;

    /** KEY-21 : une sous-clé vaut 45 jours. */
    public static final Duration VALIDITY = Duration.ofDays(45);

    private static final int CERT_SERIAL_BYTES = 16;

    public SubkeyBinding {
        Objects.requireNonNull(user, "user");
        certSerial = sized(certSerial, CERT_SERIAL_BYTES, "certSerial");
        ekId = sized(ekId, EK_ID_BYTES, "ekId");
        ekPublic = sized(ekPublic, EK_PUBLIC_BYTES, "ekPublic");
        if (Objects.requireNonNull(kem, "kem").isEmpty()) {
            throw new IllegalArgumentException("une liaison nomme son mécanisme d'encapsulation");
        }
        createdAt = millis(createdAt, "createdAt");
        expiresAt = millis(expiresAt, "expiresAt");
    }

    /** Octets signés par IK. */
    public byte[] encode() {
        return Canonical.struct(
                LABEL,
                Canonical.uuid(user.value()),
                Canonical.bytes(certSerial),
                Canonical.bytes(ekId),
                Canonical.text(kem),
                Canonical.bytes(ekPublic),
                Canonical.u64(createdAt.toEpochMilli()),
                Canonical.u64(expiresAt.toEpochMilli()));
    }

    /** Vrai si {@code signature} est celle de la liaison par la clé d'identité {@code ikPublic}. */
    public boolean isSignedBy(byte[] ikPublic, byte[] signature) {
        return Ed25519.verify(ikPublic, encode(), signature);
    }

    @Override
    public byte[] certSerial() {
        return certSerial.clone();
    }

    @Override
    public byte[] ekId() {
        return ekId.clone();
    }

    @Override
    public byte[] ekPublic() {
        return ekPublic.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SubkeyBinding that && Arrays.equals(encode(), that.encode());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encode());
    }

    @Override
    public String toString() {
        return "SubkeyBinding[ekId=" + HexFormat.of().formatHex(ekId) + ", user=" + user.value() + "]";
    }

    /** La signature porte sur des millisecondes : une date plus fine ne serait pas celle qui est signée. */
    private static Instant millis(Instant value, String name) {
        if (!Objects.requireNonNull(value, name).equals(value.truncatedTo(ChronoUnit.MILLIS))) {
            throw new IllegalArgumentException(name + " s'arrête à la milliseconde");
        }
        return value;
    }

    private static byte[] sized(byte[] value, int length, String name) {
        if (Objects.requireNonNull(value, name).length != length) {
            throw new IllegalArgumentException(name + " fait " + length + " octets");
        }
        return value.clone();
    }
}
