package fr.vlegall.seal.keys.domain.model;

import java.util.Objects;

/** Issue d'une publication de clé d'identité (KEY-03, KEY-04, ADR 0025 points 4 et 5). */
public sealed interface IdentityPublication {

    /** Certificat émis. */
    record Published(IdentityCertificate certificate) implements IdentityPublication {
        public Published {
            Objects.requireNonNull(certificate, "certificate");
        }
    }

    /** Défi inconnu, expiré, déjà utilisé ou d'un autre compte : 422 {@code CODE_EXPIRED}. */
    record ChallengeExpired() implements IdentityPublication {}

    /** Preuve qui ne vérifie pas, clé hors de la courbe comprise : 422 {@code INVALID_SIGNATURE}. */
    record InvalidSignature() implements IdentityPublication {}

    /** Identité déjà certifiée, ou compte qui n'est plus actif : 403 {@code FORBIDDEN}, jusqu'à KEY-34. */
    record Refused() implements IdentityPublication {}
}
