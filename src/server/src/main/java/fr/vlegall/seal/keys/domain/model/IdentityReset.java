package fr.vlegall.seal.keys.domain.model;

import java.util.Objects;

/**
 * Issue d'une réinitialisation de l'identité (KEY-34, ADR 0036 point 1). Le mode sudo fermé, un compte qui n'est plus
 * actif ou un remplacement refusé lèvent une exception, qui annule la transaction.
 */
public sealed interface IdentityReset {

    /** Ancien certificat révoqué, nouveau émis, coffre remplacé. */
    record Reset(IdentityCertificate certificate) implements IdentityReset {
        public Reset {
            Objects.requireNonNull(certificate, "certificate");
        }
    }

    /** Aucun coffre enregistré : il n'y a rien à remplacer ; 404 {@code KEY_NOT_FOUND}. */
    record NoVault() implements IdentityReset {}

    /** Défi inconnu, expiré, déjà utilisé ou d'un autre compte : 422 {@code CODE_EXPIRED}. */
    record ChallengeExpired() implements IdentityReset {}

    /** Preuve de possession de la nouvelle IK qui ne vérifie pas : 422 {@code INVALID_SIGNATURE}. */
    record InvalidSignature() implements IdentityReset {}

    /** Même IK que le certificat courant, rien à réinitialiser : 403 {@code FORBIDDEN}, défi consommé. */
    record SameKey() implements IdentityReset {}

    /** Coffre dont la version ne suit pas l'enregistrée : 409 {@code VAULT_VERSION_CONFLICT}. */
    record VaultConflict() implements IdentityReset {}
}
