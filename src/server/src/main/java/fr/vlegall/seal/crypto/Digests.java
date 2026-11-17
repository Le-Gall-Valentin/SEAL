package fr.vlegall.seal.crypto;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
     * HMAC-SHA256 (RFC 2104, FIPS 198-1) : empreinte à clé secrète. Sert au poivre des mots de passe (AUTH-13, ADR
     * 0026). Une clé vide est refusée : un HMAC sans secret n'apporterait rien.
     */
    public static byte[] hmacSha256(byte[] key, byte[] message) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        if (key.length == 0) {
            throw new IllegalArgumentException("HMAC-SHA256 sans clé");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException e) {
            // Toute JVM doit fournir HmacSHA256 (spécification de Mac) : son absence est une panne de plateforme.
            throw new IllegalStateException("HmacSHA256 absent de la JCA", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("clé HMAC-SHA256 refusée par la JCA", e);
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
