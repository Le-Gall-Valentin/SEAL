package fr.vlegall.seal.common.web;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/** Octets de l'API en base64url sans remplissage (PROTOCOL.md, section 1), sous leur seule forme canonique. */
public final class Base64Url {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Base64Url() {}

    public static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    /**
     * Octets d'une valeur déjà validée par {@link Base64UrlBytes} ; toute autre forme (remplissage, autre alphabet, bits
     * de fin non nuls) est une erreur de l'appelant.
     */
    public static byte[] decode(String value) {
        return parse(value)
                .orElseThrow(() -> new IllegalArgumentException("base64url sans remplissage, sous forme canonique"));
    }

    /** Vide si {@code value} n'est pas l'encodage canonique de ses octets. Linéaire, sans exception. */
    static Optional<byte[]> parse(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.chars().allMatch(Base64Url::isAlphabet) || value.length() % 4 == 1) {
            return Optional.empty();
        }
        byte[] bytes = DECODER.decode(value);
        return encode(bytes).equals(value) ? Optional.of(bytes) : Optional.empty();
    }

    private static boolean isAlphabet(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }
}
