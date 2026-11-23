package fr.vlegall.archfixture.beta.infrastructure.pki;

import fr.vlegall.archfixture.pki.application.port.in.IssueCertificateUseCase;

/** Interdit : un autre contexte que keys fait émettre un certificat. */
public class CertifyingAdapter {
    String certify(IssueCertificateUseCase issuance, String subject) {
        return issuance.issue(subject);
    }
}
