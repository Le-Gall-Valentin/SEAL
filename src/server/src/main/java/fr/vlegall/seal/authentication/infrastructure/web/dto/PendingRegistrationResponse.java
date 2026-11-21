package fr.vlegall.seal.authentication.infrastructure.web.dto;

import fr.vlegall.seal.authentication.domain.model.PendingOAuthRegistration;
import java.time.Instant;

/**
 * Corps de {@code GET /api/v1/auth/oauth/pending} (AUTH-32) : de quoi remplir Identifiant-OAuth. Le client calcule la
 * suggestion d'identifiant à partir du courriel (AUTH-04), comme à l'inscription.
 *
 * @param provider {@code registrationId} du fournisseur
 * @param displayName nom proposé par le fournisseur, peut-être vide
 * @param expiresAt fin de l'attente, en ISO 8601 UTC
 */
public record PendingRegistrationResponse(String provider, String email, String displayName, Instant expiresAt) {

    public static PendingRegistrationResponse of(PendingOAuthRegistration pending) {
        return new PendingRegistrationResponse(
                pending.identity().provider().registrationId(),
                pending.identity().email(),
                pending.identity().displayName(),
                pending.expiresAt());
    }
}
