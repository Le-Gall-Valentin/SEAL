package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import java.util.Optional;

/** KEY-34, ADR 0036 : remplacement du certificat d'un compte par l'Autorité SEAL, demandé au contexte {@code pki}. */
public interface CertificateReplacementPort {

    /**
     * Certificat de {@code ikPublic} qui remplace celui du compte, révoqué motif « remplacé » ; vide si c'est déjà la
     * clé du certificat non révoqué, ou si une publication concurrente l'a devancé.
     */
    Optional<IdentityCertificate> supersede(UserId subject, String username, byte[] ikPublic);
}
