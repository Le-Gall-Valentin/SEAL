package fr.vlegall.seal.pki.infrastructure.web.dto;

import fr.vlegall.seal.pki.domain.model.AuthorityKey;
import java.util.Base64;

/** {@code GET /api/v1/pki/ca} (PROTOCOL.md, section 4.1) : octets en base64url sans remplissage. */
public record AuthorityView(String publicKey, String keyId, String fingerprint) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static AuthorityView of(AuthorityKey authority) {
        return new AuthorityView(
                ENCODER.encodeToString(authority.publicKey()),
                ENCODER.encodeToString(authority.keyId()),
                authority.fingerprint());
    }
}
