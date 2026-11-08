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

    /**
     * Compare deux empreintes en temps constant (A04) : la durée ne dépend pas de la position du premier octet
     * différent, donc une mesure du temps de réponse ne révèle pas un préfixe correct.
     */
    public static boolean equal(byte[] expected, byte[] actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        return MessageDigest.isEqual(expected, actual);
    }
}
