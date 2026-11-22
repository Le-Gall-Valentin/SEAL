package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST /api/v1/auth/reauth} (AUTH-55, ADR 0034). {@link #toString()} ne révèle pas le mot de passe. */
public record ReauthRequest(@NotNull @Size(max = 512) String password) {

    @Override
    public String toString() {
        return "ReauthRequest[password=***]";
    }
}
