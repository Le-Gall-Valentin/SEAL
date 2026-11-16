package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * KEY-04 : émission du certificat d'identité, une fois la preuve de possession vérifiée par le contexte {@code keys}.
 * Appelé depuis un autre contexte : la signature ne porte que des types de la JDK, {@link UserId} et l'interface
 * {@link Issued}, rangée ici même ; son implémentation reste dans {@code pki} (ADR 0008).
 */
public interface IssueCertificateUseCase {

    /** Champs du certificat émis (PROTOCOL.md, section 4.2) et signature de la CA, octets bruts. */
    interface Issued {
        byte[] serial();

        byte[] issuerKeyId();

        UserId subject();

        String username();

        byte[] ikPublic();

        Instant notBefore();

        Instant notAfter();

        int usage();

        byte[] signature();
    }

    /**
     * Émet, signe et enregistre le certificat de {@code ikPublic}, dans la transaction de l'appelant s'il en a une.
     * Vide si le titulaire a déjà un certificat valide ; un certificat expiré est révoqué, motif « remplacé » (ADR 0025,
     * point 4).
     */
    Optional<Issued> issue(UserId subject, String username, byte[] ikPublic);
}
