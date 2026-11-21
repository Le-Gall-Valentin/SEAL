package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.infrastructure.web.ValidDisplayName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de {@code PATCH /api/v1/account/profile} (ACC-03). Borne de SEC-01 avant normalisation, comme l'inscription. */
public record UpdateProfileRequest(
        @NotNull @Size(max = 200) @ValidDisplayName String displayName) {

    /** Appelé après la validation : le nom est alors bien formé. */
    public DisplayName toDisplayName() {
        return DisplayName.parse(displayName).orElseThrow();
    }
}
