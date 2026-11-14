package fr.vlegall.seal.pki.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Certificat non révoqué, tel que la base le garde : sans l'identifiant, qui ne vit que dans ses octets signés
 * ({@code identity_certificates}, CDC section 13). Il peut être expiré.
 */
public record CertificateRecord(byte[] serial, UserId subject, byte[] ikPublic, Instant notBefore, Instant notAfter) {

    public CertificateRecord {
        serial = Objects.requireNonNull(serial, "serial").clone();
        Objects.requireNonNull(subject, "subject");
        ikPublic = Objects.requireNonNull(ikPublic, "ikPublic").clone();
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
    }

    /** Enregistrement d'un certificat qui vient d'être émis. */
    public static CertificateRecord of(SealCertificate certificate) {
        return new CertificateRecord(
                certificate.serial(),
                certificate.subject(),
                certificate.ikPublic(),
                certificate.notBefore(),
                certificate.notAfter());
    }

    /** Vrai de {@code notBefore} inclus à {@code notAfter} exclu, comme {@link SealCertificate#isValidAt}. */
    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(notBefore) && instant.isBefore(notAfter);
    }

    @Override
    public byte[] serial() {
        return serial.clone();
    }

    @Override
    public byte[] ikPublic() {
        return ikPublic.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CertificateRecord that
                && Arrays.equals(serial, that.serial)
                && subject.equals(that.subject)
                && Arrays.equals(ikPublic, that.ikPublic)
                && notBefore.equals(that.notBefore)
                && notAfter.equals(that.notAfter);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serial);
    }

    @Override
    public String toString() {
        return "CertificateRecord[serial=" + HexFormat.of().formatHex(serial) + ", subject=" + subject.value() + "]";
    }
}
