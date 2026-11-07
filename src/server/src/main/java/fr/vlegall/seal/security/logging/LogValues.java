package fr.vlegall.seal.security.logging;

import java.util.Objects;

/**
 * Prépare une valeur venue de l'extérieur avant de la journaliser (SEC-21, A05) : les courriels sont masqués et les
 * caractères de contrôle neutralisés, pour qu'une saisie ne puisse pas forger de fausse ligne de journal.
 */
public final class LogValues {

    private static final String MASK = "***";
    private static final char REPLACEMENT = '_';

    private LogValues() {}

    /** {@code valentin@exemple.ca} devient {@code v***@exemple.ca} ; sans partie locale, seul {@code ***} reste. */
    public static String maskEmail(String email) {
        Objects.requireNonNull(email, "email");
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        return sanitize(email.substring(0, 1)) + MASK + sanitize(email.substring(at));
    }

    /** Remplace tout caractère de contrôle, ainsi que les séparateurs de ligne Unicode U+2028 et U+2029, par {@code _}. */
    public static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder safe = new StringBuilder(value.length());
        value.chars().forEach(c -> safe.append(isLineBreaking(c) ? REPLACEMENT : (char) c));
        return safe.toString();
    }

    /** Identifiant saisi (identifiant ou courriel) : masqué s'il contient {@code @}, assaini sinon. */
    public static String identifier(String value) {
        Objects.requireNonNull(value, "value");
        return value.indexOf('@') >= 0 ? maskEmail(value) : sanitize(value);
    }

    private static boolean isLineBreaking(int c) {
        return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
    }
}
