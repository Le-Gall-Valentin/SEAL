package fr.vlegall.seal.pki.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.pki.domain.model.AuthorityKey;

/** {@code GET /api/v1/pki/ca} (PROTOCOL.md, section 4.1) : octets en base64url sans remplissage. */
public record AuthorityView(String publicKey, String keyId, String fingerprint) {

    public static AuthorityView of(AuthorityKey authority) {
        return new AuthorityView(
                Base64Url.encode(authority.publicKey()), Base64Url.encode(authority.keyId()), authority.fingerprint());
    }
}
