package fr.vlegall.seal.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Empreintes cryptographiques, par la JCA seule (ADR 0001). */
public final class Digests {

    private Digests() {}

    /** SHA-256 (FIPS 180-4). */
    public static byte[] sha256(byte[] input) {
        Objects.requireNonNull(input, "input");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // Toute JVM doit fournir SHA-256 (spécification de MessageDigest) : son absence est une panne de
            // plateforme.
            throw new IllegalStateException("SHA-256 absent de la JCA", e);
        }
    }
}
