package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.application.port.in.CurrentCertificateQuery;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CurrentCertificateHandler implements CurrentCertificateQuery {

    private final CertificateRepository certificates;
    private final Clock clock;

    public CurrentCertificateHandler(CertificateRepository certificates, Clock clock) {
        this.certificates = certificates;
        this.clock = clock;
    }

    /** Relu en base à chaque appel (médiation complète, ADR 0025) : jamais de cache. */
    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateDetails> current(UserId subject) {
        return certificates
                .findUnrevoked(subject)
                .filter(certificate -> certificate.isValidAt(clock.instant()))
                .flatMap(certificate -> certificates.findIssued(certificate.serial()))
                .map(CertificateDetailsView::new);
    }
}
