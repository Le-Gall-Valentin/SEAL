package fr.vlegall.seal.crypto;

import java.io.Serial;

/** Valeur hors du domaine de l'encodage canonique (ADR 0006) : elle est rejetée, jamais encodée. */
public final class CanonicalEncodingException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CanonicalEncodingException(String message) {
        super(message);
    }
}
