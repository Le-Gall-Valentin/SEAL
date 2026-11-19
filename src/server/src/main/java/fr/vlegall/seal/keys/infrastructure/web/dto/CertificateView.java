package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import java.time.Instant;
import java.util.UUID;

/**
 * Certificat rendu par {@code PUT /api/v1/keys/identity} et par {@code GET /api/v1/users/{userId}/keys} : les champs
 * de {@code SealCertificate} (PROTOCOL.md, section 4.2), dates en ISO 8601 à la milliseconde, et la signature de la CA.
 * Le client réencode la structure et la vérifie avec la clé épinglée avant de l'enregistrer (KEY-06).
 */
public record CertificateView(
        String serial,
        String issuerKeyId,
        UUID subjectId,
        String username,
        String ikPublic,
        Instant notBefore,
        Instant notAfter,
        int usage,
        String signature) {

    public static CertificateView of(IdentityCertificate certificate) {
        return new CertificateView(
                Base64Url.encode(certificate.serial()),
                Base64Url.encode(certificate.issuerKeyId()),
                certificate.subject().value(),
                certificate.username(),
                Base64Url.encode(certificate.ikPublic()),
                certificate.notBefore(),
                certificate.notAfter(),
                certificate.usage(),
                Base64Url.encode(certificate.signature()));
    }
}
