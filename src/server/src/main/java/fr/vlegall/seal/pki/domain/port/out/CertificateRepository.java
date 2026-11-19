package fr.vlegall.seal.pki.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import java.time.Instant;
import java.util.Optional;

/** Table {@code identity_certificates} : au plus un certificat non révoqué par titulaire (ADR 0025, point 4). */
public interface CertificateRepository {

    /** Le certificat non révoqué du titulaire, même expiré. */
    Optional<CertificateRecord> findUnrevoked(UserId subject);

    /** KEY-33 : le certificat émis sous ce numéro de série, relu depuis ses octets signés, avec la signature de la CA. */
    Optional<IssuedCertificate> findIssued(byte[] serial);

    /** Faux si le titulaire a déjà un certificat non révoqué : une publication concurrente l'a devancé. */
    boolean save(IssuedCertificate certificate);

    void revoke(byte[] serial, Instant at, RevocationReason reason);
}
