package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.Unicode;
import java.util.Objects;
import java.util.Optional;

/** Nom affiché (AUTH-10), modifiable ensuite : 1 à 50 points de code après NFC, sans caractère piégé (SEC-01). */
public record DisplayName(String value) {

    /** AUTH-10, en points de code, comme {@code char_length} de PostgreSQL. */
    public static final int MAX_LENGTH = 50;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("nom affiché invalide");
        }
    }

    /** Nom saisi, normalisé en NFC ; vide s'il enfreint une règle. */
    public static Optional<DisplayName> parse(String raw) {
        String normalized = Unicode.nfc(raw);
        return isValid(normalized) ? Optional.of(new DisplayName(normalized)) : Optional.empty();
    }

    private static boolean isValid(String value) {
        int length = value.codePointCount(0, value.length());
        return length >= 1
                && length <= MAX_LENGTH
                && Unicode.isNfc(value)
                && value.codePoints().noneMatch(DisplayName::isForbidden)
                // Un nom fait seulement d'espaces, même insécables, serait invisible.
                && value.codePoints().anyMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c));
    }

    /**
     * SEC-01 : caractères de contrôle, séparateurs de ligne et de paragraphe, et caractères de direction (U+061C,
     * U+200E, U+200F, U+202A à U+202E, U+2066 à U+2069), qui pourraient faire lire un nom à l'envers.
     */
    private static boolean isForbidden(int c) {
        int type = Character.getType(c);
        return type == Character.CONTROL
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || c == 0x061C
                || c == 0x200E
                || c == 0x200F
                || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069);
    }
}
