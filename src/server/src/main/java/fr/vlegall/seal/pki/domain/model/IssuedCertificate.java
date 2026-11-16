package fr.vlegall.seal.pki.domain.model;

import java.util.Arrays;
import java.util.Objects;

/** Certificat émis et signature de la CA sur ses octets canoniques (KEY-04). */
public record IssuedCertificate(SealCertificate certificate, byte[] signature) {

    public IssuedCertificate {
        Objects.requireNonNull(certificate, "certificate");
        signature = Objects.requireNonNull(signature, "signature").clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IssuedCertificate that
                && certificate.equals(that.certificate)
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return 31 * certificate.hashCode() + Arrays.hashCode(signature);
    }

    @Override
    public String toString() {
        return "IssuedCertificate[" + certificate + "]";
    }
}
