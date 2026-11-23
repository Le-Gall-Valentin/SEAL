package fr.vlegall.archfixture.keys.infrastructure.pki;

import fr.vlegall.archfixture.pki.application.port.in.IssueCertificateUseCase;
import fr.vlegall.archfixture.pki.application.port.in.ReplaceCertificateUseCase;

/** Permis : l'adaptateur de keys, après sa preuve de possession et son mode sudo, demande le certificat à pki. */
public class CertificationAdapter {
    String certify(IssueCertificateUseCase issuance, ReplaceCertificateUseCase replacement, String subject) {
        return issuance.issue(subject) + replacement.supersede(subject);
    }
}
