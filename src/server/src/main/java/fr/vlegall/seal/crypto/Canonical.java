package fr.vlegall.seal.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Encodage canonique des structures signées ou hachées (CDC section 7.2, PROTOCOL.md section 2 ; MSG-01).
 *
 * <p>Chaque méthode reprend une primitive de la grammaire et renvoie ses octets, si bien qu'une structure s'écrit
 * comme dans la spécification : {@code struct("SEAL-aad-v1", u8(1), text(suite), uuid(messageId), ...)}. Une valeur
 * hors domaine lève {@link CanonicalEncodingException} (ADR 0006) ; une valeur nulle lève
 * {@link NullPointerException} : un champ absent s'écrit explicitement comme une chaîne d'octets vide.
 */
public final class Canonical {

    private static final long U32_MAX = 0xFFFF_FFFFL;

    /** ADR 0006 : forme 8-4-4-4-12 exactement ; UUID.fromString accepterait « 1-2-3-4-5 ». */
    private static final Pattern UUID_FORM =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private Canonical() {}

    /** {@code u8(x)} : 1 octet, de 0 à 255. */
    public static byte[] u8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new CanonicalEncodingException("u8 hors de 0..255 : " + value);
        }
        return new byte[] {(byte) value};
    }

    /** {@code u32(x)} : 4 octets gros-boutistes, de 0 à 2^32-1. */
    public static byte[] u32(long value) {
        if (value < 0 || value > U32_MAX) {
            throw new CanonicalEncodingException("u32 hors de 0..2^32-1 : " + value);
        }
        return ByteBuffer.allocate(Integer.BYTES).putInt((int) value).array();
    }

    /** {@code u64(x)} : 8 octets gros-boutistes, de 0 à 2^63-1 (ADR 0006). */
    public static byte[] u64(long value) {
        if (value < 0) {
            throw new CanonicalEncodingException("u64 négatif : " + value);
        }
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    /** {@code bytes(b)} : {@code u32(longueur de b) || b}. */
    public static byte[] bytes(byte[] value) {
        Objects.requireNonNull(value, "bytes : valeur nulle");
        return concat(u32(value.length), value);
    }

    /**
     * {@code text(s)} : {@code bytes(UTF-8(NFC(s)))}. Un demi-caractère Unicode isolé est rejeté plutôt que remplacé
     * en silence (ADR 0006).
     */
    public static byte[] text(String value) {
        Objects.requireNonNull(value, "text : valeur nulle");
        CharsetEncoder strictUtf8 = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer encoded = strictUtf8.encode(CharBuffer.wrap(Normalizer.normalize(value, Normalizer.Form.NFC)));
            byte[] utf8 = new byte[encoded.remaining()];
            encoded.get(utf8);
            return bytes(utf8);
        } catch (CharacterCodingException e) {
            throw new CanonicalEncodingException("text : demi-caractère Unicode isolé");
        }
    }

    /** {@code uuid(u)} : 16 octets dans l'ordre réseau, depuis la forme textuelle 8-4-4-4-12 (ADR 0006). */
    public static byte[] uuid(String value) {
        Objects.requireNonNull(value, "uuid : valeur nulle");
        if (!UUID_FORM.matcher(value).matches()) {
            throw new CanonicalEncodingException("uuid hors de la forme 8-4-4-4-12");
        }
        // Les cinq groupes sont lus à leur position dans la chaîne validée, qui n'est plus modifiée.
        HexFormat hex = HexFormat.of();
        return concat(
                hex.parseHex(value, 0, 8),
                hex.parseHex(value, 9, 13),
                hex.parseHex(value, 14, 18),
                hex.parseHex(value, 19, 23),
                hex.parseHex(value, 24, 36));
    }

    /** {@code uuid(u)} : 16 octets dans l'ordre réseau. */
    public static byte[] uuid(UUID value) {
        Objects.requireNonNull(value, "uuid : valeur nulle");
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    /**
     * {@code list(T, xs)} : {@code u32(nombre d'éléments) || T(x1) || ... || T(xn)}. Le tri éventuel des éléments
     * revient à la structure qui appelle.
     */
    public static <T> byte[] list(Function<? super T, byte[]> element, List<? extends T> items) {
        Objects.requireNonNull(element, "list : encodeur nul");
        Objects.requireNonNull(items, "list : éléments nuls");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(u32(items.size()));
        for (T item : items) {
            out.writeBytes(Objects.requireNonNull(element.apply(item), "list : élément encodé nul"));
        }
        return out.toByteArray();
    }

    /** {@code a || b || ...} : concaténation, pour les éléments composés d'une liste ou le texte clair d'un message. */
    public static byte[] concat(byte[]... parts) {
        Objects.requireNonNull(parts, "concat : parties nulles");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(Objects.requireNonNull(part, "concat : partie nulle"));
        }
        return out.toByteArray();
    }

    /**
     * {@code struct(label, f1, ..., fn)} : {@code text(label) || f1 || ... || fn}. L'étiquette, non vide, sépare les
     * domaines : une signature d'un type de structure ne vaut pas pour un autre.
     */
    public static byte[] struct(String label, byte[]... fields) {
        Objects.requireNonNull(label, "struct : étiquette nulle");
        if (label.isEmpty()) {
            throw new CanonicalEncodingException("struct : étiquette vide");
        }
        Objects.requireNonNull(fields, "struct : champs nuls");
        return concat(text(label), concat(fields));
    }
}
