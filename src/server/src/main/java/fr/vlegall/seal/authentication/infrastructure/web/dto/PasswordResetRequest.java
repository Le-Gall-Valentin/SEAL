package fr.vlegall.seal.authentication.infrastructure.web.dto;

import fr.vlegall.seal.common.web.ValidEmail;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AUTH-70 : courriel du compte, de la forme d'ADR 0019 point 5 ; bornes de SEC-01 (ADR 0022, mise à jour). */
public record PasswordResetRequest(
        @NotNull @Size(max = 254) @ValidEmail String email) {

    /** SEC-21 : le courriel n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "PasswordResetRequest[email=***]";
    }
}
