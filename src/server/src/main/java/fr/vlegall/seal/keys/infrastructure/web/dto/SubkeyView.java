package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.domain.model.PublishedSubkey;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import java.time.Instant;

/**
 * Sous-clé servie par {@code GET /api/v1/users/{userId}/keys} : les champs de la liaison (PROTOCOL.md, section 4.4),
 * sauf le numéro de série, porté par le certificat, et la signature de l'IK ; dates en ISO 8601 à la milliseconde.
 */
public record SubkeyView(
        String ekId, String kem, String ekPublic, Instant createdAt, Instant expiresAt, String signature) {

    public static SubkeyView of(PublishedSubkey subkey) {
        SubkeyBinding binding = subkey.binding();
        return new SubkeyView(
                Base64Url.encode(binding.ekId()),
                binding.kem(),
                Base64Url.encode(binding.ekPublic()),
                binding.createdAt(),
                binding.expiresAt(),
                Base64Url.encode(subkey.signature()));
    }
}
