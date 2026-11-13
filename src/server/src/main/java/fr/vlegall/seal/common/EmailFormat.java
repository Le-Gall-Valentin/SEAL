package fr.vlegall.seal.common;

/**
 * Forme d'une adresse électronique (ADR 0019, point 5), partagée par {@code identity} et {@code authentication} (ADR 0022,
 * mise à jour) : la règle n'est écrite qu'une fois. Vérifications linéaires, sans expression régulière.
 */
public final class EmailFormat {

    /** Longueur maximale d'une adresse (RFC 5321, chemin complet). */
    public static final int MAX_LENGTH = 254;

    private static final int LOCAL_PART_MAX_LENGTH = 64;
    private static final int LABEL_MAX_LENGTH = 63;
    private static final String ATEXT_SYMBOLS = "!#$%&'*+-/=?^_`{|}~";

    private EmailFormat() {}

    /**
     * Forme {@code partie-locale@domaine} (ADR 0019, point 5) : partie locale en « dot-atom » de la RFC 5322, étendue
     * aux caractères non ASCII (RFC 6531) hors contrôle, format, espace, demi-paire isolée, usage privé et point non
     * attribué ; domaine en libellés ASCII (un nom internationalisé s'écrit en punycode), au moins deux. Une valeur qui
     * n'est pas en NFC est refusée : l'appelant normalise d'abord.
     */
    public static boolean isValid(String value) {
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
        return local.codePoints().allMatch(EmailFormat::isLocalPartCharacter);
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
