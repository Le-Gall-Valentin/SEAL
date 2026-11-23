package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.pki.domain.model.AuthorityKey;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;

/** Autorité de test : une paire Ed25519 tirée pour chaque instance. */
final class TestAuthority implements AuthoritySigner {

    private final Ed25519.GeneratedKey ca = Ed25519.generate();

    @Override
    public AuthorityKey key() {
        return new AuthorityKey(ca.publicKey());
    }

    @Override
    public byte[] sign(byte[] message) {
        return Ed25519.sign(ca.privateKey(), message);
    }

    byte[] publicKey() {
        return ca.publicKey();
    }
}
