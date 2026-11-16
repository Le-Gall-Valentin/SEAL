package fr.vlegall.seal.pki.domain.model;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Canonical;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Certificat d'identité SEAL (KEY-04, CDC section 6.2, PROTOCOL.md section 4.2) : la CA atteste qu'une clé Ed25519
 * appartient à un compte et à son identifiant définitif (KEY-12). Les dates sont à la milliseconde, comme dans la
 * structure signée.
 */
public record SealCertificate(
        byte[] serial,
        byte[] issuerKeyId,
        UserId subject,
        String username,
        byte[] ikPublic,
        Instant notBefore,
        Instant notAfter,
        int usage) {

    /** Étiquette de la structure : elle fixe aussi l'algorithme, Ed25519 (ADR 0025, point 11). */
    public static final String LABEL = "SEAL-cert-v1";

    /** Numéro de série : 16 octets aléatoires. */
    public static final int SERIAL_BYTES = 16;

    /** {@code issuerKeyId} : 8 premiers octets de SHA-256 de la clé CA. */
    public static final int ISSUER_KEY_ID_BYTES = 8;

    /** Clé publique Ed25519 brute. */
    public static final int PUBLIC_KEY_BYTES = 32;

    /** Section 6.2 : notAfter = notBefore + 397 jours. */
    public static final Duration VALIDITY = Duration.ofDays(397);

    /** Bit 0 : signature et non-répudiation ; bit 1 : certification de sous-clés. */
    public static final int USAGE = 0b11;

    public SealCertificate {
        serial = sized(serial, SERIAL_BYTES, "serial");
        issuerKeyId = sized(issuerKeyId, ISSUER_KEY_ID_BYTES, "issuerKeyId");
        ikPublic = sized(ikPublic, PUBLIC_KEY_BYTES, "ikPublic");
        Objects.requireNonNull(subject, "subject");
        if (Objects.requireNonNull(username, "username").isEmpty()) {
            throw new IllegalArgumentException("un certificat porte l'identifiant de son titulaire");
        }
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
        if (!notAfter.equals(notBefore.plus(VALIDITY)) || !notBefore.equals(notBefore.truncatedTo(ChronoUnit.MILLIS))) {
            throw new IllegalArgumentException("un certificat vaut 397 jours, à la milliseconde");
        }
        if (usage < 0 || usage > 0xFF) {
            throw new IllegalArgumentException("usage hors d'un octet");
        }
    }

    /** KEY-04 : certificat émis à {@code now}, ramené à la milliseconde, valable 397 jours, usage {@link #USAGE}. */
    public static SealCertificate issue(
            byte[] serial, byte[] issuerKeyId, UserId subject, String username, byte[] ikPublic, Instant now) {
        Instant notBefore = now.truncatedTo(ChronoUnit.MILLIS);
        return new SealCertificate(
                serial, issuerKeyId, subject, username, ikPublic, notBefore, notBefore.plus(VALIDITY), USAGE);
    }

    /** Octets signés par la CA (PROTOCOL.md, section 4.2). */
    public byte[] encode() {
        return Canonical.struct(
                LABEL,
                Canonical.bytes(serial),
                Canonical.bytes(issuerKeyId),
                Canonical.uuid(subject.value()),
                Canonical.text(username),
                Canonical.bytes(ikPublic),
                Canonical.u64(notBefore.toEpochMilli()),
                Canonical.u64(notAfter.toEpochMilli()),
                Canonical.u8(usage));
    }

    @Override
    public byte[] serial() {
        return serial.clone();
    }

    @Override
    public byte[] issuerKeyId() {
        return issuerKeyId.clone();
    }

    @Override
    public byte[] ikPublic() {
        return ikPublic.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SealCertificate that && Arrays.equals(encode(), that.encode());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encode());
    }

    @Override
    public String toString() {
        return "SealCertificate[serial=" + HexFormat.of().formatHex(serial) + ", subject=" + subject.value() + "]";
    }

    private static byte[] sized(byte[] value, int length, String name) {
        if (Objects.requireNonNull(value, name).length != length) {
            throw new IllegalArgumentException(name + " fait " + length + " octets");
        }
        return value.clone();
    }
}
