package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** ADR 0022, point 1 : jeton lu dans le fragment du lien, transmis dans le corps, jamais dans l'URL. */
public record PasswordResetInspectRequest(
        @NotNull @Pattern(regexp = PasswordResetTokens.PATTERN)
        String token) {

    @Override
    public String toString() {
        return "PasswordResetInspectRequest[token=***]";
    }
}
