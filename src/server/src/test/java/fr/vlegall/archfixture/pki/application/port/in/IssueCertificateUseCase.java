package fr.vlegall.archfixture.pki.application.port.in;

/** Port de pki qui émet un certificat sans vérifier la preuve, réservé à l'adaptateur keys/infrastructure/pki. */
public interface IssueCertificateUseCase {

    String issue(String subject);
}
