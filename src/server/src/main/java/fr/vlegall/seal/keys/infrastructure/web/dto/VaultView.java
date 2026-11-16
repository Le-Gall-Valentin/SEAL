package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.domain.model.Vault;

/** Réponse de {@code GET /api/v1/keys/vault} : les champs du {@code PUT}, octets en base64url. */
public record VaultView(long vaultVersion, String salt, String nonceK, String wrappedVdk, String nonceB, String blob) {

    public static VaultView of(Vault vault) {
        return new VaultView(
                vault.version(),
                Base64Url.encode(vault.salt()),
                Base64Url.encode(vault.nonceK()),
                Base64Url.encode(vault.wrappedVdk()),
                Base64Url.encode(vault.nonceB()),
                Base64Url.encode(vault.blob()));
    }

    @Override
    public String toString() {
        return "VaultView[vaultVersion=" + vaultVersion + ", blob=***]";
    }
}
