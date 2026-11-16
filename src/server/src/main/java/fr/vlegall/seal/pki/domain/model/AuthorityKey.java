package fr.vlegall.seal.pki.domain.model;

import fr.vlegall.seal.crypto.Digests;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Clé publique de l'Autorité SEAL (KEY-10, PROTOCOL.md section 4.1), avec son identifiant et son empreinte. */
public record AuthorityKey(byte[] publicKey) {

    public AuthorityKey {
        if (Objects.requireNonNull(publicKey, "publicKey").length != SealCertificate.PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("une clé Ed25519 fait 32 octets");
        }
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    /** {@code issuerKeyId} des certificats : les 8 premiers octets de SHA-256 de la clé brute. */
    public byte[] keyId() {
        return Arrays.copyOf(Digests.sha256(publicKey), SealCertificate.ISSUER_KEY_ID_BYTES);
    }

    /** KEY-10 : empreinte journalisée au démarrage, SHA-256 de la clé brute en hexadécimal majuscule. */
    public String fingerprint() {
        return HexFormat.of().withUpperCase().formatHex(Digests.sha256(publicKey));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthorityKey that && Arrays.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(publicKey);
    }

    @Override
    public String toString() {
        return "AuthorityKey[" + fingerprint() + "]";
    }
}
