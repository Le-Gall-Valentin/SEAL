package fr.vlegall.seal.common;

/**
 * ACC-03, AUTH-10, SEC-01 : règle du nom affiché, partagée par {@code identity}, qui la garde à l'inscription et aux
 * paramètres, par {@code authentication}, qui en tire le nom proposé d'une inscription OAuth (ADR 0032), et par
 * {@code conversations}, qui l'applique au nom d'un groupe sur 64 points de code (GRP-01, ADR 0039, point 6). La règle
 * n'est écrite qu'une fois, comme {@link EmailFormat} ; le client en garde la copie dans
 * {@code entities/account/model/displayName.ts} (ADR 0040).
 */
public final class DisplayNameRule {

    /** AUTH-10, en points de code, comme {@code char_length} de PostgreSQL. */
    public static final int MAX_LENGTH = 50;

    /** ZERO WIDTH NON-JOINER et ZERO WIDTH JOINER, seuls caractères de format permis (ADR 0040). */
    private static final int ZWNJ = 0x200C;

    private static final int ZWJ = 0x200D;

    private DisplayNameRule() {}

    /** Vrai si le nom, déjà normalisé en NFC, respecte la règle : 1 à 50 points de code, visible, sans caractère piégé. */
    public static boolean isValid(String value) {
        return isValid(value, MAX_LENGTH);
    }

    /**
     * Même règle à une autre longueur : GRP-01 et l'ADR 0039 (point 6) la reprennent pour le nom d'un groupe, sur 64
     * points de code.
     */
    public static boolean isValid(String value, int maxLength) {
        int length = value.codePointCount(0, value.length());
        return length >= 1
                && length <= maxLength
                && Unicode.isNfc(value)
                && value.codePoints().noneMatch(DisplayNameRule::isForbidden)
                // Un nom fait seulement d'espaces ou de caractères invisibles ne se verrait pas.
                && value.codePoints().anyMatch(DisplayNameRule::isVisible);
    }

    /** Lettre, chiffre, symbole ou ponctuation, hors des caractères de remplissage qui s'affichent vides. */
    private static boolean isVisible(int c) {
        if (c == 0x115F || c == 0x1160 || c == 0x3164 || c == 0xFFA0 || c == 0x2800) {
            return false;
        }
        return switch (Character.getType(c)) {
            case Character.UPPERCASE_LETTER,
                    Character.LOWERCASE_LETTER,
                    Character.TITLECASE_LETTER,
                    Character.MODIFIER_LETTER,
                    Character.OTHER_LETTER,
                    Character.DECIMAL_DIGIT_NUMBER,
                    Character.LETTER_NUMBER,
                    Character.OTHER_NUMBER,
                    Character.MATH_SYMBOL,
                    Character.CURRENCY_SYMBOL,
                    Character.MODIFIER_SYMBOL,
                    Character.OTHER_SYMBOL,
                    Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    /**
     * SEC-01, ACC-03 : caractères de contrôle, séparateurs de ligne et de paragraphe, demi-paires de substitution
     * isolées (que l'encodage UTF-8 vers la base remplacerait par « ? ») et points de code non attribués. S'y ajoutent
     * (ADR 0040) tous les caractères de format sauf ZWNJ (U+200C, écritures persane et indiennes) et ZWJ (U+200D,
     * émojis composés) : les caractères de direction (U+061C, U+200E, U+200F, U+202A à U+202E, U+2066 à U+2069), qui
     * pourraient faire lire un nom à l'envers, mais aussi U+200B, U+2060, U+FEFF, U+00AD et les étiquettes, qui ne
     * s'affichent pas ; et l'usage privé, dont l'affichage dépend de la police. Un nom ne peut ainsi pas en imiter un
     * autre par un caractère invisible.
     */
    public static boolean isForbidden(int c) {
        return switch (Character.getType(c)) {
            case Character.CONTROL,
                    Character.SURROGATE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.PRIVATE_USE -> true;
            case Character.FORMAT -> c != ZWNJ && c != ZWJ;
            default -> false;
        };
    }
}
