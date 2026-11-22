package fr.vlegall.seal.authentication.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Corps de {@code POST /api/v1/account/password} (ACC-01, ADR 0034). L'option « déconnecter mes autres sessions » est
 * cochée par défaut : absente, elle vaut vrai. {@link #toString()} ne révèle aucun mot de passe.
 */
public record ChangePasswordRequest(
        @NotNull @Size(max = 512) String currentPassword,
        @NotNull @Size(max = 512) String newPassword,
        @Nullable Boolean signOutOtherSessions) {

    public boolean signOut() {
        return signOutOtherSessions == null || signOutOtherSessions;
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***, signOutOtherSessions="
                + signOutOtherSessions + "]";
    }
}
