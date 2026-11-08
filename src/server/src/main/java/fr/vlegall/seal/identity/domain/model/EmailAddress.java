package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.Unicode;
import java.util.Objects;
import java.util.Optional;

/**
 * Adresse électronique d'un compte, normalisée en NFC (SEC-01) et gardée telle que saisie : PostgreSQL la compare
 * sans tenir compte de la casse ({@code citext}). {@link #toString()} ne la révèle pas (SEC-21).
 */
public record EmailAddress(String value) {

    /** Longueur maximale d'une adresse (RFC 5321, chemin complet). */
    public static final int MAX_LENGTH = 254;

    private static final int LOCAL_PART_MAX_LENGTH = 64;
    private static final int LABEL_MAX_LENGTH = 63;
    private static final String ATEXT_SYMBOLS = "!#$%&'*+-/=?^_`{|}~";

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("adresse invalide");
        }
    }

    /** Adresse saisie, normalisée en NFC ; vide si elle est mal formée. */
    public static Optional<EmailAddress> parse(String raw) {
        String normalized = Unicode.nfc(raw);
        return isValid(normalized) ? Optional.of(new EmailAddress(normalized)) : Optional.empty();
    }

    /** Partie locale avant le premier {@code +} ou {@code @} (AUTH-04, étape 1 ; AUTH-11, ADR 0019). */
    public String localPart() {
        int at = value.indexOf('@');
        int plus = value.indexOf('+');
        return value.substring(0, plus >= 0 && plus < at ? plus : at);
    }

    @Override
    public String toString() {
        return "EmailAddress[***]";
    }

    /**
     * Forme {@code partie-locale@domaine} : partie locale en « dot-atom » de la RFC 5322, étendue aux caractères non
     * ASCII (RFC 6531) hors contrôle, format, espace, demi-paire isolée, usage privé et point non attribué ; domaine en libellés ASCII (un nom internationalisé s'écrit
     * en punycode), au moins deux.
     */
    private static boolean isValid(String value) {
        if (value.length() > MAX_LENGTH || !Unicode.isNfc(value)) {
            return false;
        }
        int at = value.indexOf('@');
        if (at <= 0 || value.indexOf('@', at + 1) >= 0) {
            return false;
        }
        return isLocalPart(value.substring(0, at)) && isDomain(value.substring(at + 1));
    }

    private static boolean isLocalPart(String local) {
        if (local.length() > LOCAL_PART_MAX_LENGTH
                || local.startsWith(".")
                || local.endsWith(".")
                || local.contains("..")) {
            return false;
        }
        return local.codePoints().allMatch(EmailAddress::isLocalPartCharacter);
    }

    private static boolean isLocalPartCharacter(int c) {
        if (c < 0x80) {
            return (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || ATEXT_SYMBOLS.indexOf(c) >= 0;
        }
        int type = Character.getType(c);
        return type != Character.CONTROL
                && type != Character.SURROGATE
                && type != Character.UNASSIGNED
                && type != Character.PRIVATE_USE
                && type != Character.FORMAT
                && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR
                && type != Character.SPACE_SEPARATOR;
    }

    private static boolean isDomain(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!isLabel(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLabel(String label) {
        if (label.isEmpty() || label.length() > LABEL_MAX_LENGTH || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        return label.chars()
                .allMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-');
    }
}
