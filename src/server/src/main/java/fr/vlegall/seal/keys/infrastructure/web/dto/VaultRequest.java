package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.common.web.Base64UrlBytes;
import fr.vlegall.seal.keys.domain.model.Vault;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corps de {@code PUT /api/v1/keys/vault} (KEY-32, CDC section 6.4, ADR 0025 point 6), opaque pour le serveur. */
public record VaultRequest(
        @NotNull @Min(1) Long vaultVersion,

        @NotNull @Base64UrlBytes(min = Vault.SALT_BYTES, max = Vault.SALT_BYTES)
        String salt,

        @NotNull @Base64UrlBytes(min = Vault.NONCE_BYTES, max = Vault.NONCE_BYTES)
        String nonceK,

        @NotNull @Base64UrlBytes(min = Vault.WRAPPED_VDK_BYTES, max = Vault.WRAPPED_VDK_BYTES)
        String wrappedVdk,

        @NotNull @Base64UrlBytes(min = Vault.NONCE_BYTES, max = Vault.NONCE_BYTES)
        String nonceB,

        @NotNull @Base64UrlBytes(min = Vault.MIN_BLOB_BYTES, max = Vault.MAX_BLOB_BYTES)
        String blob) {

    /** Appelé après la validation : chaque champ a alors sa taille. */
    public Vault toVault() {
        return new Vault(
                vaultVersion,
                Base64Url.decode(salt),
                Base64Url.decode(nonceK),
                Base64Url.decode(wrappedVdk),
                Base64Url.decode(nonceB),
                Base64Url.decode(blob));
    }

    @Override
    public String toString() {
        return "VaultRequest[vaultVersion=" + vaultVersion + ", blob=***]";
    }
}
