package fr.vlegall.seal.pki.domain.port.out;

import fr.vlegall.seal.pki.domain.model.AuthorityKey;

/** Clé de l'Autorité SEAL (KEY-10) : sa partie publique, et la signature avec sa partie privée, qui ne sort pas. */
public interface AuthoritySigner {

    AuthorityKey key();

    /** Signature Ed25519 de {@code message}, toujours les octets canoniques d'une structure étiquetée. */
    byte[] sign(byte[] message);
}
