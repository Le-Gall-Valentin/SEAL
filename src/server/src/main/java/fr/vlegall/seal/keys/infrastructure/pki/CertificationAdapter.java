package fr.vlegall.seal.keys.infrastructure.pki;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.pki.application.port.in.CurrentCertificateQuery;
import fr.vlegall.seal.pki.application.port.in.IssueCertificateUseCase;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Fait certifier une clé par le contexte {@code pki}, par ses ports d'entrée (ADR 0008, règle 7 ; ADR 0025, point 13). */
@Component
public class CertificationAdapter implements CertificationPort {

    private final IssueCertificateUseCase issuance;
    private final CurrentCertificateQuery currentCertificates;

    public CertificationAdapter(IssueCertificateUseCase issuance, CurrentCertificateQuery currentCertificates) {
        this.issuance = issuance;
        this.currentCertificates = currentCertificates;
    }

    @Override
    public Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic) {
        return issuance.issue(subject, username, ikPublic)
                .map(issued -> new IdentityCertificate(
                        issued.serial(),
                        issued.issuerKeyId(),
                        issued.subject(),
                        issued.username(),
                        issued.ikPublic(),
                        issued.notBefore(),
                        issued.notAfter(),
                        issued.usage(),
                        issued.signature()));
    }

    @Override
    public Optional<CurrentCertificate> current(UserId subject) {
        return currentCertificates
                .current(subject)
                .map(certificate -> new CurrentCertificate(certificate.serial(), certificate.ikPublic()));
    }
}
