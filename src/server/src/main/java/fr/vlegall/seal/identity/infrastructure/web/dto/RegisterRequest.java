package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;
import fr.vlegall.seal.identity.infrastructure.web.ValidDisplayName;
import fr.vlegall.seal.identity.infrastructure.web.ValidEmail;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/v1/auth/register} (AUTH-10). Bornes de SEC-01 avant normalisation ; le format de
 * l'identifiant est jugé par le domaine ({@code USERNAME_INVALID}, ADR 0019). {@link #toString()} ne révèle ni le
 * courriel ni le mot de passe.
 */
public record RegisterRequest(
        @NotNull @Size(max = 254) @ValidEmail String email,
        @NotNull @Size(max = 64) String username,
        @NotNull @Size(max = 200) @ValidDisplayName String displayName,
        @NotNull @Size(max = 512) String password) {

    /** Appelé après la validation : courriel et nom affiché sont alors bien formés. */
    public RegisterCommand toCommand() {
        return new RegisterCommand(
                EmailAddress.parse(email).orElseThrow(),
                username,
                DisplayName.parse(displayName).orElseThrow(),
                password);
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***]";
    }
}
