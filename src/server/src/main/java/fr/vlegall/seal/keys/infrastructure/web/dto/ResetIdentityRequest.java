package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64UrlBytes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code POST /api/v1/keys/identity/reset} (KEY-34, ADR 0036 point 1) : preuve de possession de la nouvelle IK
 * (KEY-03) et nouveau coffre, aux bornes de {@code PUT /keys/vault}. Pas de {@code userId} : la preuve est vérifiée
 * pour le compte de la session.
 */
public record ResetIdentityRequest(
        @NotNull @Base64UrlBytes(min = 32, max = 32) String ikPublic,
        @NotNull @Base64UrlBytes(min = 32, max = 32) String challenge,
        @NotNull @Base64UrlBytes(min = 64, max = 64) String signature,
        @NotNull @Valid VaultRequest vault) {

    /** SEC-21 : ni le défi, ni la signature, ni le coffre dans une trace. */
    @Override
    public String toString() {
        return "ResetIdentityRequest[ikPublic=" + ikPublic + ", challenge=***, signature=***, vault=" + vault + "]";
    }
}
