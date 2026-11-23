package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UsernameRule;
import java.util.Locale;
import java.util.Optional;

/** ADR 0037, point 6 : saisie de l'identifiant à inviter, normalisée comme le fait le client. */
public final class InvitedUsername {

    private InvitedUsername() {}

    /**
     * Retire les espaces autour et un {@code @} initial, passe en minuscules, puis exige la forme d'AUTH-01 ; vide pour
     * toute autre saisie.
     */
    public static Optional<String> normalize(String input) {
        String trimmed = input.strip();
        String bare = trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
        String lower = bare.toLowerCase(Locale.ROOT);
        return UsernameRule.isWellFormed(lower) ? Optional.of(lower) : Optional.empty();
    }
}
