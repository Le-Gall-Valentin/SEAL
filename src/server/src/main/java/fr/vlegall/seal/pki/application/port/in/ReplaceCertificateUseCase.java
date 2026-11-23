package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * KEY-34, ADR 0036 (point 1) : remplacement du certificat d'un compte qui réinitialise son identité, une fois le mode
 * sudo et la preuve de possession vérifiés par le contexte {@code keys}. Mêmes types que {@link IssueCertificateUseCase}
 * (ADR 0008).
 */
public interface ReplaceCertificateUseCase {

    /**
     * Révoque le certificat non révoqué du titulaire s'il en a un, même valide, motif « remplacé » (ADR 0036, point 5),
     * puis émet, signe et enregistre celui de {@code ikPublic}, dans la transaction de l'appelant. Vide si
     * {@code ikPublic} est déjà la clé du certificat non révoqué, ou si une publication concurrente a devancé
     * l'enregistrement : l'appelant annule alors sa transaction, révocation comprise.
     */
    Optional<CertificateDetails> supersede(UserId subject, String username, byte[] ikPublic);
}
