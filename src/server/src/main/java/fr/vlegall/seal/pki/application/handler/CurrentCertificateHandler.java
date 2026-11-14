package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.application.port.in.CurrentCertificateQuery;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
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
    public Optional<Current> current(UserId subject) {
        return certificates
                .findUnrevoked(subject)
                .filter(certificate -> certificate.isValidAt(clock.instant()))
                .map(CurrentView::new);
    }

    private record CurrentView(CertificateRecord certificate) implements Current {

        @Override
        public byte[] serial() {
            return certificate.serial();
        }

        @Override
        public byte[] ikPublic() {
            return certificate.ikPublic();
        }
    }
}
