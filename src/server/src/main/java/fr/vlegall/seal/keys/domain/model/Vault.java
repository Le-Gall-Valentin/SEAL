package fr.vlegall.seal.keys.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Coffre de clés (KEY-32, CDC section 6.4), opaque pour le serveur : le trousseau chiffré par la VDK, et la VDK chiffrée
 * par une clé dérivée de la clé de récupération, que le serveur ne voit jamais (KEY-31).
 */
public record Vault(long version, byte[] salt, byte[] nonceK, byte[] wrappedVdk, byte[] nonceB, byte[] blob) {

    /** Sel de HKDF. */
    public static final int SALT_BYTES = 32;

    /** Nonce d'AES-256-GCM. */
    public static final int NONCE_BYTES = 12;

    /** VDK de 32 octets chiffrée, plus l'étiquette GCM de 16. */
    public static final int WRAPPED_VDK_BYTES = 48;

    /** Étiquette GCM : le plus petit blob possible. */
    public static final int MIN_BLOB_BYTES = 16;

    /** ADR 0025, point 6 : 32 Kio, pour qu'un corps en base64 tienne dans 64 Kio. */
    public static final int MAX_BLOB_BYTES = 32 * 1024;

    public Vault {
        if (version < 1) {
            throw new IllegalArgumentException("la première version d'un coffre est 1");
        }
        salt = sized(salt, SALT_BYTES, SALT_BYTES, "salt");
        nonceK = sized(nonceK, NONCE_BYTES, NONCE_BYTES, "nonceK");
        wrappedVdk = sized(wrappedVdk, WRAPPED_VDK_BYTES, WRAPPED_VDK_BYTES, "wrappedVdk");
        nonceB = sized(nonceB, NONCE_BYTES, NONCE_BYTES, "nonceB");
        blob = sized(blob, MIN_BLOB_BYTES, MAX_BLOB_BYTES, "blob");
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] nonceK() {
        return nonceK.clone();
    }

    @Override
    public byte[] wrappedVdk() {
        return wrappedVdk.clone();
    }

    @Override
    public byte[] nonceB() {
        return nonceB.clone();
    }

    @Override
    public byte[] blob() {
        return blob.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Vault that
                && version == that.version
                && Arrays.equals(salt, that.salt)
                && Arrays.equals(nonceK, that.nonceK)
                && Arrays.equals(wrappedVdk, that.wrappedVdk)
                && Arrays.equals(nonceB, that.nonceB)
                && Arrays.equals(blob, that.blob);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(version) * 31 + Arrays.hashCode(blob);
    }

    /** Rien du contenu chiffré dans une trace. */
    @Override
    public String toString() {
        return "Vault[version=" + version + ", blob=" + blob.length + " octets]";
    }

    private static byte[] sized(byte[] value, int min, int max, String name) {
        int length = Objects.requireNonNull(value, name).length;
        if (length < min || length > max) {
            throw new IllegalArgumentException(name + " hors de " + min + " à " + max + " octets");
        }
        return value.clone();
    }
}
