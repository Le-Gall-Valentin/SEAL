package fr.vlegall.seal.keys.infrastructure.pki;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
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
        return issuance.issue(subject, username, ikPublic).map(CertificationAdapter::toCertificate);
    }

    @Override
    public Optional<IdentityCertificate> current(UserId subject) {
        return currentCertificates.current(subject).map(CertificationAdapter::toCertificate);
    }

    private static IdentityCertificate toCertificate(CertificateDetails details) {
        return new IdentityCertificate(
                details.serial(),
                details.issuerKeyId(),
                details.subject(),
                details.username(),
                details.ikPublic(),
                details.notBefore(),
                details.notAfter(),
                details.usage(),
                details.signature());
    }
}
