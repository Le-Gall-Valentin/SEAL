package fr.vlegall.seal.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Ed25519 pur (RFC 8032), par la JCA seule (ADR 0001, ADR 0025). Clés publiques et signatures circulent en octets
 * bruts (32 et 64 octets), clés privées en PKCS#8 : aucun type de {@code java.security} ne sort de ce package.
 */
public final class Ed25519 {

    /** Taille d'une clé publique brute. */
    public static final int PUBLIC_KEY_BYTES = 32;

    /** Taille d'une signature. */
    public static final int SIGNATURE_BYTES = 64;

    private static final String ALGORITHM = "Ed25519";

    /** En-tête DER d'un SubjectPublicKeyInfo Ed25519 (RFC 8410, OID 1.3.101.112), suivi des 32 octets de la clé. */
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private Ed25519() {}

    /** Paire tirée par le générateur de la JCA : clé privée en PKCS#8, clé publique brute. */
    public record GeneratedKey(byte[] privateKey, byte[] publicKey) {

        public GeneratedKey {
            privateKey = privateKey.clone();
            publicKey = publicKey.clone();
        }

        @Override
        public byte[] privateKey() {
            return privateKey.clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GeneratedKey key
                    && Arrays.equals(privateKey, key.privateKey)
                    && Arrays.equals(publicKey, key.publicKey);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(privateKey) + Arrays.hashCode(publicKey);
        }

        /** Jamais la clé privée dans un journal (SEC-21). */
        @Override
        public String toString() {
            return "GeneratedKey[privateKey=***, publicKey=" + HexFormat.of().formatHex(publicKey) + "]";
        }
    }

    /** KEY-10 : nouvelle paire, tirée par le {@code SecureRandom} par défaut de la JCA. */
    public static GeneratedKey generate() {
        KeyPair pair = generator().generateKeyPair();
        return new GeneratedKey(
                pair.getPrivate().getEncoded(),
                fromSubjectPublicKeyInfo(pair.getPublic().getEncoded()));
    }

    /** Signe {@code message} avec la clé privée PKCS#8 ; une clé illisible est une erreur de l'appelant. */
    public static byte[] sign(byte[] privateKeyPkcs8, byte[] message) {
        Objects.requireNonNull(message, "message");
        try {
            PrivateKey key = keyFactory()
                    .generatePrivate(new PKCS8EncodedKeySpec(Objects.requireNonNull(privateKeyPkcs8, "privateKey")));
            Signature signer = signature();
            signer.initSign(key);
            signer.update(message);
            return signer.sign();
        } catch (InvalidKeySpecException | InvalidKeyException | SignatureException e) {
            throw new IllegalArgumentException("clé privée Ed25519 illisible", e);
        }
    }

    /**
     * Vrai si {@code signature} est celle de {@code message} par {@code publicKey}. Une entrée d'un attaquant (longueur
     * fausse, clé hors de la courbe, signature mal formée) donne faux, jamais une exception (ADR 0025).
     */
    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(signature, "signature");
        if (publicKey.length != PUBLIC_KEY_BYTES || signature.length != SIGNATURE_BYTES) {
            return false;
        }
        try {
            PublicKey key = keyFactory().generatePublic(new X509EncodedKeySpec(toSubjectPublicKeyInfo(publicKey)));
            Signature verifier = signature();
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            // Clé hors de la courbe (InvalidKeyException) ou signature mal formée (SignatureException).
            return false;
        }
    }

    /** ADR 0025, point 12 : clé publique brute de 32 octets, encodée en SubjectPublicKeyInfo (RFC 8410). */
    public static byte[] toSubjectPublicKeyInfo(byte[] publicKey) {
        if (Objects.requireNonNull(publicKey, "publicKey").length != PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("une clé publique Ed25519 fait 32 octets");
        }
        return Canonical.concat(SPKI_PREFIX, publicKey);
    }

    /** ADR 0025, point 12 : les 32 octets d'un SubjectPublicKeyInfo Ed25519, refusé s'il porte une autre clé. */
    public static byte[] fromSubjectPublicKeyInfo(byte[] spki) {
        Objects.requireNonNull(spki, "spki");
        if (spki.length != SPKI_PREFIX.length + PUBLIC_KEY_BYTES
                || !Arrays.equals(spki, 0, SPKI_PREFIX.length, SPKI_PREFIX, 0, SPKI_PREFIX.length)) {
            throw new IllegalArgumentException("SubjectPublicKeyInfo qui n'est pas une clé Ed25519");
        }
        return Arrays.copyOfRange(spki, SPKI_PREFIX.length, spki.length);
    }

    // Toute JVM 15 ou plus fournit Ed25519 (JEP 339) : son absence est une panne de plateforme, pas une erreur à
    // traiter. Les trois fabriques la signalent une fois chacune.

    private static KeyPairGenerator generator() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 absent de la JCA", e);
        }
    }

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 absent de la JCA", e);
        }
    }

    private static Signature signature() {
        try {
            return Signature.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 absent de la JCA", e);
        }
    }
}
