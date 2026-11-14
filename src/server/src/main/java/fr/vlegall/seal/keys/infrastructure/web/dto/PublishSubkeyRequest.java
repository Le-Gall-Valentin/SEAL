package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64UrlBytes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/**
 * Corps de {@code POST /api/v1/keys/subkeys} (KEY-05, PROTOCOL.md section 4.4). Pas de {@code userId} : la liaison
 * est vérifiée pour le compte de la session. Dates en ISO 8601, à la milliseconde, comme dans la structure signée.
 */
public record PublishSubkeyRequest(
        @NotNull @Base64UrlBytes(min = 16, max = 16) String certSerial,
        @NotNull @Base64UrlBytes(min = 16, max = 16) String ekId,
        // ADR 0025, point 7 : X-Wing est P2.
        @NotNull @Pattern(regexp = "^DHKEM-X25519-HKDF-SHA256$")
        String kem,

        @NotNull @Base64UrlBytes(min = 32, max = 32) String ekPublic,
        @NotNull Instant createdAt,
        @NotNull Instant expiresAt,
        @NotNull @Base64UrlBytes(min = 64, max = 64) String signature) {}
