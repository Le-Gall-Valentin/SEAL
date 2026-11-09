package fr.vlegall.seal.security.logging;

import java.util.Objects;

/**
 * Prépare une valeur venue de l'extérieur avant de la journaliser (SEC-21, A05) : les courriels sont masqués et les
 * caractères de contrôle neutralisés, pour qu'une saisie ne puisse pas forger de fausse ligne de journal.
 */
public final class LogValues {

    private static final String MASK = "***";
    private static final char REPLACEMENT = '_';

    /** Longueur maximale d'une adresse électronique (RFC 5321, chemin complet). */
    private static final int EMAIL_MAX_LENGTH = 254;

    private LogValues() {}

    /**
     * {@code valentin@exemple.ca} devient {@code v***@exemple.ca} : le premier point de code reste entier, émoji
     * compris. Sans partie locale, seul {@code ***} reste.
     */
    public static String maskEmail(String email) {
        Objects.requireNonNull(email, "email");
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        int first = Character.charCount(email.codePointAt(0));
        return sanitize(email.substring(0, first)) + MASK + sanitize(email.substring(at));
    }

    /**
     * Remplace par {@code _} tout caractère de contrôle, les séparateurs de ligne Unicode U+2028 et U+2029, et les
     * caractères de direction (U+202A à U+202E, U+2066 à U+2069), qui pourraient faire lire une ligne à l'envers.
     */
    public static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder safe = new StringBuilder(value.length());
        value.chars().forEach(c -> safe.append(isLineBreaking(c) ? REPLACEMENT : (char) c));
        return safe.toString();
    }

    /** Courriel saisi : masqué s'il a strictement la forme d'un courriel, {@code ***} sinon (ADR 0013, point 1). */
    public static String email(String value) {
        Objects.requireNonNull(value, "value");
        return looksLikeEmail(value) ? maskEmail(value) : MASK;
    }

    /**
     * Un seul {@code @}, partie locale non vide et sans espace, domaine fait de libellés {@code [A-Za-z0-9-]} séparés
     * par des points, avec au moins un point ; 254 caractères au plus. Vérification linéaire, sans expression régulière.
     */
    private static boolean looksLikeEmail(String value) {
        int at = value.indexOf('@');
        if (value.length() > EMAIL_MAX_LENGTH || at <= 0 || value.indexOf('@', at + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < at; i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return isDomain(value.substring(at + 1));
    }

    private static boolean isDomain(String domain) {
        int labels = 0;
        int labelLength = 0;
        for (int i = 0; i < domain.length(); i++) {
            char c = domain.charAt(i);
            if (c == '.') {
                if (labelLength == 0) {
                    return false;
                }
                labels++;
                labelLength = 0;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-') {
                labelLength++;
            } else {
                return false;
            }
        }
        return labelLength > 0 && labels >= 1;
    }

    private static boolean isLineBreaking(int c) {
        return Character.isISOControl(c)
                || c == '\u2028'
                || c == '\u2029'
                || (c >= '\u202A' && c <= '\u202E')
                || (c >= '\u2066' && c <= '\u2069');
    }
}
