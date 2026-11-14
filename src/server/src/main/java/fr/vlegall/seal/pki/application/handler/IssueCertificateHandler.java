package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.IssueCertificateUseCase;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import fr.vlegall.seal.pki.domain.port.out.RandomBytes;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class IssueCertificateHandler implements IssueCertificateUseCase {

    private final AuthoritySigner signer;
    private final CertificateRepository certificates;
    private final RandomBytes random;
    private final Clock clock;

    public IssueCertificateHandler(
            AuthoritySigner signer, CertificateRepository certificates, RandomBytes random, Clock clock) {
        this.signer = signer;
        this.certificates = certificates;
        this.random = random;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<Issued> issue(UserId subject, String username, byte[] ikPublic) {
        Instant now = clock.instant();
        Optional<CertificateRecord> unrevoked = certificates.findUnrevoked(subject);
        if (unrevoked.isPresent()) {
            if (unrevoked.get().isValidAt(now)) {
                return Optional.empty();
            }
            certificates.revoke(unrevoked.get().serial(), now, RevocationReason.SUPERSEDED);
        }
        SealCertificate certificate = SealCertificate.issue(
                random.next(SealCertificate.SERIAL_BYTES), signer.key().keyId(), subject, username, ikPublic, now);
        IssuedCertificate issued = new IssuedCertificate(certificate, signer.sign(certificate.encode()));
        return certificates.save(issued) ? Optional.of(new IssuedView(issued)) : Optional.empty();
    }

    private record IssuedView(IssuedCertificate issued) implements Issued {

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
}
