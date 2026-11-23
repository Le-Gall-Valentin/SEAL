package fr.vlegall.archfixture.pki.application.port.in;

/** Port de pki qui révoque et remplace un certificat sans vérifier ni sudo ni preuve, réservé à keys/infrastructure/pki. */
public interface ReplaceCertificateUseCase {

    String supersede(String subject);
}
