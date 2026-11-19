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

    /**
     * KEY-33 : le certificat courant du titulaire (non révoqué, valable à {@code at}), relu depuis ses octets signés,
     * avec la signature de la CA. Une seule lecture : une révocation concurrente ne peut pas glisser entre le choix du
     * certificat et sa relecture (ADR 0025, médiation complète).
     */
    Optional<IssuedCertificate> findCurrentIssued(UserId subject, Instant at);

    /** Faux si le titulaire a déjà un certificat non révoqué : une publication concurrente l'a devancé. */
    boolean save(IssuedCertificate certificate);

    void revoke(byte[] serial, Instant at, RevocationReason reason);
}
