package fr.vlegall.seal.crypto;

import java.security.SecureRandom;

/** Tirages aléatoires par {@link SecureRandom} de la JCA (ADR 0001, A04), jamais par {@code java.util.Random}. */
public final class Randoms {

    /** {@link SecureRandom} est sûr entre fils d'exécution : une seule instance suffit. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private Randoms() {}

    /** Chaîne de {@code length} chiffres ASCII tirés uniformément, par exemple le code d'AUTH-14. */
    public static String digits(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("longueur strictement positive attendue : " + length);
        }
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append((char) ('0' + RANDOM.nextInt(10)));
        }
        return digits.toString();
    }

    /** {@code count} octets tirés uniformément, par exemple le jeton de 256 bits d'AUTH-70. */
    public static byte[] bytes(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("nombre d'octets strictement positif attendu : " + count);
        }
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
