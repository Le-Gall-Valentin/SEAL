package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * Certificat valide d'un compte, non révoqué et non expiré, demandé par le contexte {@code keys} pour vérifier une
 * liaison de sous-clé (KEY-05). La signature ne porte que des types de la JDK, {@link UserId} et l'interface
 * {@link Current}, rangée ici même ; son implémentation reste dans {@code pki} (ADR 0008).
 */
public interface CurrentCertificateQuery {

    /** Numéro de série du certificat et clé d'identité qu'il atteste. */
    interface Current {
        byte[] serial();

        byte[] ikPublic();
    }

    Optional<Current> current(UserId subject);
}
