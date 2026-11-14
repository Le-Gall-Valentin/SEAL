package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import java.util.Optional;

/** L'Autorité SEAL, demandée au contexte {@code pki} (ADR 0025, point 13). */
public interface CertificationPort {

    /** Certificat émis pour {@code ikPublic} ; vide si le compte a déjà un certificat valide (ADR 0025, point 4). */
    Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic);

    /** Certificat non révoqué et non expiré du compte. */
    Optional<CurrentCertificate> current(UserId subject);
}
