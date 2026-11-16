package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64UrlBytes;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /api/v1/keys/identity} (KEY-03, PROTOCOL.md section 4.3). Pas de {@code userId} : la preuve est
 * vérifiée pour le compte de la session.
 */
public record PublishIdentityRequest(
        @NotNull @Base64UrlBytes(min = 32, max = 32) String ikPublic,
        @NotNull @Base64UrlBytes(min = 32, max = 32) String challenge,
        @NotNull @Base64UrlBytes(min = 64, max = 64) String signature) {

    /** SEC-21 : ni le défi ni la signature dans une trace. */
    @Override
    public String toString() {
        return "PublishIdentityRequest[ikPublic=" + ikPublic + ", challenge=***, signature=***]";
    }
}
