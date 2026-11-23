package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import fr.vlegall.seal.pki.domain.port.out.RandomBytes;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** KEY-04, KEY-34 : signe un nouveau certificat et l'enregistre ; partagé par l'émission et le remplacement. */
final class CertificateSigning {

    private final AuthoritySigner signer;
    private final CertificateRepository certificates;
    private final RandomBytes random;

    CertificateSigning(AuthoritySigner signer, CertificateRepository certificates, RandomBytes random) {
        this.signer = Objects.requireNonNull(signer, "signer");
        this.certificates = Objects.requireNonNull(certificates, "certificates");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Vide si le titulaire a déjà un certificat non révoqué : une publication concurrente l'a devancé. */
    Optional<CertificateDetails> signAndSave(UserId subject, String username, byte[] ikPublic, Instant now) {
        SealCertificate certificate = SealCertificate.issue(
                random.next(SealCertificate.SERIAL_BYTES), signer.key().keyId(), subject, username, ikPublic, now);
        IssuedCertificate issued = new IssuedCertificate(certificate, signer.sign(certificate.encode()));
        return certificates.save(issued) ? Optional.of(new CertificateDetailsView(issued)) : Optional.empty();
    }
}
