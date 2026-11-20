package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * Certificat valide d'un compte, non révoqué et non expiré, demandé par le contexte {@code keys} pour vérifier une
 * liaison de sous-clé (KEY-05) et le servir à son titulaire (KEY-33). La signature ne porte que des types de la JDK,
 * {@link UserId} et {@link CertificateDetails} ; son implémentation reste dans {@code pki} (ADR 0008).
 */
public interface CurrentCertificateQuery {

    Optional<CertificateDetails> current(UserId subject);
}
