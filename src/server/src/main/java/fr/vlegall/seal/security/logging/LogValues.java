package fr.vlegall.seal.security.logging;

import java.util.Objects;

/**
 * Prépare une valeur venue de l'extérieur avant de la journaliser (SEC-21, A05) : les courriels sont masqués et les
 * caractères de contrôle neutralisés, pour qu'une saisie ne puisse pas forger de fausse ligne de journal.
 */
public final class LogValues {

    private static final String MASK = "***";
    private static final char REPLACEMENT = '_';

    /** AUTH-01 : un identifiant fait au plus 20 caractères. */
    private static final int USERNAME_MAX_LENGTH = 20;

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

    /**
     * Identifiant saisi dans un champ « identifiant ou courriel » : un courriel est masqué, une valeur qui pourrait
     * être un identifiant (AUTH-01 : au plus 20 caractères parmi minuscules, chiffres, {@code .} et {@code _}) est
     * gardée, tout le reste devient {@code ***}. Un mot de passe tapé par erreur dans ce champ n'est ainsi pas
     * journalisé (SEC-21).
     */
    public static String identifier(String value) {
        Objects.requireNonNull(value, "value");
        if (value.indexOf('@') >= 0) {
            return maskEmail(value);
        }
        return couldBeUsername(value) ? value : MASK;
    }

    /** Vérification linéaire, sans expression régulière : il ne s'agit que de décider ce qu'on journalise. */
    private static boolean couldBeUsername(String value) {
        return !value.isEmpty()
                && value.length() <= USERNAME_MAX_LENGTH
                && value.chars()
                        .allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_');
    }

    private static boolean isLineBreaking(int c) {
        return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
    }
}
