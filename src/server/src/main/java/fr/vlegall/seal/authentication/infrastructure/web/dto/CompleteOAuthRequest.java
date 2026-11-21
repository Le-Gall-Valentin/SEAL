package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/v1/auth/oauth/complete} (AUTH-32). Bornes de SEC-01 avant normalisation, celles de
 * l'inscription ; l'identifiant et le nom affiché sont jugés par le domaine ({@code USERNAME_INVALID},
 * {@code VALIDATION_FAILED}).
 */
public record CompleteOAuthRequest(
        @NotNull @Size(max = 64) String username,
        @NotNull @Size(max = 200) String displayName) {}
