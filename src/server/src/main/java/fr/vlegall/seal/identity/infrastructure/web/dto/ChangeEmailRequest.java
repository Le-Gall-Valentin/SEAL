package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.infrastructure.web.ValidEmail;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST /api/v1/auth/verify-email/change} (AUTH-17). {@link #toString()} ne révèle pas l'adresse. */
public record ChangeEmailRequest(
        @NotNull @Size(max = 254) @ValidEmail String email) {

    public EmailAddress toEmail() {
        return EmailAddress.parse(email).orElseThrow();
    }

    @Override
    public String toString() {
        return "ChangeEmailRequest[email=***]";
    }
}
