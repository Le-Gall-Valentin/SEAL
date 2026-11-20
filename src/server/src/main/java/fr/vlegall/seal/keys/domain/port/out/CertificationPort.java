package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import java.util.Optional;

/** L'Autorité SEAL, demandée au contexte {@code pki} (ADR 0025, point 13). */
public interface CertificationPort {

    /**
     * Certificat émis pour {@code ikPublic} ; vide si le compte a déjà un certificat valide, ou un certificat expiré
     * d'une autre clé d'identité (ADR 0025, point 4).
     */
    Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic);

    /** Certificat non révoqué et non expiré du compte, entier, avec la signature de la CA (KEY-05, KEY-33). */
    Optional<IdentityCertificate> current(UserId subject);
}
