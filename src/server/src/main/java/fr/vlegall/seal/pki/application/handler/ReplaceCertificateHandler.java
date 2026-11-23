package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.application.port.in.ReplaceCertificateUseCase;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import fr.vlegall.seal.pki.domain.port.out.RandomBytes;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ReplaceCertificateHandler implements ReplaceCertificateUseCase {

    private final CertificateRepository certificates;
    private final CertificateSigning signing;
    private final Clock clock;

    public ReplaceCertificateHandler(
            AuthoritySigner signer, CertificateRepository certificates, RandomBytes random, Clock clock) {
        this.certificates = certificates;
        this.signing = new CertificateSigning(signer, certificates, random);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<CertificateDetails> supersede(UserId subject, String username, byte[] ikPublic) {
        Instant now = clock.instant();
        Optional<CertificateRecord> unrevoked = certificates.findUnrevoked(subject);
        if (unrevoked.isPresent()) {
            if (Arrays.equals(unrevoked.get().ikPublic(), ikPublic)) {
                return Optional.empty();
            }
            // ADR 0036, point 5 : seule la clé de récupération est perdue, rien ne dit que l'ancienne IK est
            // compromise.
            certificates.revoke(unrevoked.get().serial(), now, RevocationReason.SUPERSEDED);
        }
        return signing.signAndSave(subject, username, ikPublic, now);
    }
}
