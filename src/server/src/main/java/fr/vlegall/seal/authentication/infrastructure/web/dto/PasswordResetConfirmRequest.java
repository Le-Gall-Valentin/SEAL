package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** AUTH-70 : jeton du lien et nouveau mot de passe ; bornes de SEC-01 (ADR 0022, point 6). */
public record PasswordResetConfirmRequest(
        @NotNull @Pattern(regexp = PasswordResetTokens.PATTERN)
        String token,

        @NotNull @Size(max = 512) String password) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, password=***]";
    }
}
