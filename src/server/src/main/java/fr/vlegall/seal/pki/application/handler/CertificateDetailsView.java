package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import java.time.Instant;

/** Un certificat émis, vu des autres contextes (ADR 0008) : ses champs et la signature de la CA. */
record CertificateDetailsView(IssuedCertificate issued) implements CertificateDetails {

    @Override
    public byte[] serial() {
        return issued.certificate().serial();
    }

    @Override
    public byte[] issuerKeyId() {
        return issued.certificate().issuerKeyId();
    }

    @Override
    public UserId subject() {
        return issued.certificate().subject();
    }

    @Override
    public String username() {
        return issued.certificate().username();
    }

    @Override
    public byte[] ikPublic() {
        return issued.certificate().ikPublic();
    }

    @Override
    public Instant notBefore() {
        return issued.certificate().notBefore();
    }

    @Override
    public Instant notAfter() {
        return issued.certificate().notAfter();
    }

    @Override
    public int usage() {
        return issued.certificate().usage();
    }

    @Override
    public byte[] signature() {
        return issued.signature();
    }
}
