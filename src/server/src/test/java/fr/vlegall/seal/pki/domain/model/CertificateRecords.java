package fr.vlegall.seal.pki.domain.model;

/** Doublures de test : ce que la table {@code identity_certificates} rend d'un certificat émis. */
public final class CertificateRecords {

    private CertificateRecords() {}

    /** Tout le certificat, sauf l'identifiant, qui ne vit que dans ses octets signés (CDC section 13). */
    public static CertificateRecord of(SealCertificate certificate) {
        return new CertificateRecord(
                certificate.serial(),
                certificate.subject(),
                certificate.ikPublic(),
                certificate.notBefore(),
                certificate.notAfter());
    }
}
