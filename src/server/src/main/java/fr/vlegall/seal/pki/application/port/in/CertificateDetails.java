package fr.vlegall.seal.pki.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;

/**
 * Champs d'un certificat d'identité (PROTOCOL.md, section 4.2) et signature de la CA, octets bruts : ce que {@code pki}
 * livre aux autres contextes, à l'émission (KEY-04) comme à la relecture (KEY-05, KEY-33). Seuls des types de la JDK
 * et {@link UserId} ; l'implémentation reste dans {@code pki} (ADR 0008).
 */
public interface CertificateDetails {
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
