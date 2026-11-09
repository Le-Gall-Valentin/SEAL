package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.VerificationCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Corps de {@code POST /api/v1/auth/verify-email} (AUTH-14). {@link #toString()} ne révèle pas le code. */
public record VerifyEmailRequest(
        @NotNull @Pattern(regexp = "^[0-9]{6}$") String code) {

    /** Appelé après la validation : le code compte alors exactement 6 chiffres ASCII. */
    public VerificationCode toCode() {
        return VerificationCode.parse(code).orElseThrow();
    }

    @Override
    public String toString() {
        return "VerifyEmailRequest[code=******]";
    }
}
