package fr.vlegall.seal.keys.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Certificat d'identité rendu au client après la preuve de possession (KEY-04) : ses champs (PROTOCOL.md, section 4.2)
 * et la signature de la CA, que le client vérifie avant de l'enregistrer (KEY-06).
 */
public record IdentityCertificate(
        byte[] serial,
        byte[] issuerKeyId,
        UserId subject,
        String username,
        byte[] ikPublic,
        Instant notBefore,
        Instant notAfter,
        int usage,
        byte[] signature) {

    public IdentityCertificate {
        serial = Objects.requireNonNull(serial, "serial").clone();
        issuerKeyId = Objects.requireNonNull(issuerKeyId, "issuerKeyId").clone();
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(username, "username");
        ikPublic = Objects.requireNonNull(ikPublic, "ikPublic").clone();
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
        signature = Objects.requireNonNull(signature, "signature").clone();
    }

    /** Vrai si {@code candidate} est le numéro de série de ce certificat. */
    public boolean hasSerial(byte[] candidate) {
        return Arrays.equals(serial, candidate);
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
    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdentityCertificate that
                && Arrays.equals(serial, that.serial)
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serial);
    }

    @Override
    public String toString() {
        return "IdentityCertificate[serial=" + HexFormat.of().formatHex(serial) + ", subject=" + subject.value() + "]";
    }
}
