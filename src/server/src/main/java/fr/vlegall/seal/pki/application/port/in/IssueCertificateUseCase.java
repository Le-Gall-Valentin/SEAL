package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * KEY-04 : émission du certificat d'identité, une fois la preuve de possession vérifiée par le contexte {@code keys}.
 * Appelé depuis un autre contexte : la signature ne porte que des types de la JDK, {@link UserId} et l'interface
 * {@link CertificateDetails} ; son implémentation reste dans {@code pki} (ADR 0008).
 */
public interface IssueCertificateUseCase {

    /**
     * Émet, signe et enregistre le certificat de {@code ikPublic}, dans la transaction de l'appelant s'il en a une.
     * Vide si le titulaire a déjà un certificat valide, ou un certificat expiré d'une autre clé d'identité (son
     * remplacement relève de {@link ReplaceCertificateUseCase}, en mode sudo, KEY-34) ; un certificat expiré de la
     * même clé est révoqué, motif « remplacé » (ADR 0025, point 4).
     */
    Optional<CertificateDetails> issue(UserId subject, String username, byte[] ikPublic);
}
