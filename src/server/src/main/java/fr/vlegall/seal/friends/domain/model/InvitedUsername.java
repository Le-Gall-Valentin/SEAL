package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UsernameRule;
import java.util.Locale;
import java.util.Optional;

/** ADR 0037, point 6 : saisie de l'identifiant à inviter, normalisée comme le fait le client. */
public final class InvitedUsername {

    private InvitedUsername() {}

    /**
     * Retire les espaces ASCII (U+0020) autour et un {@code @} initial, refuse tout caractère hors de l'ASCII imprimable,
     * passe en minuscules, puis exige la forme d'AUTH-01 ; vide pour toute autre saisie (ADR 0037, points 6 et 10).
     */
    public static Optional<String> normalize(String input) {
        String trimmed = stripSpaces(input);
        String bare = trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
        if (!bare.chars().allMatch(c -> c >= 0x21 && c <= 0x7E)) {
            return Optional.empty();
        }
        String lower = bare.toLowerCase(Locale.ROOT);
        return UsernameRule.isWellFormed(lower) ? Optional.of(lower) : Optional.empty();
    }

    /** Espaces U+0020 seulement : {@code strip()} retirerait aussi des caractères de contrôle (U+001C à U+001F). */
    private static String stripSpaces(String input) {
        int start = 0;
        int end = input.length();
        while (start < end && input.charAt(start) == ' ') {
            start++;
        }
        while (end > start && input.charAt(end - 1) == ' ') {
            end--;
        }
        return input.substring(start, end);
    }
}
