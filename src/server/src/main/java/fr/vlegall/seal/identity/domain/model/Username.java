package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.UsernameRule;
import java.util.Objects;
import java.util.Optional;

/** Identifiant d'un compte, définitif (AUTH-03) : la forme est vérifiée à la construction (AUTH-01). */
public record Username(String value) {

    public Username {
        Objects.requireNonNull(value, "value");
        if (!UsernameRule.isWellFormed(value)) {
            throw new IdentityException.UsernameInvalid();
        }
    }

    /** Identifiant saisi, accepté tel quel ou refusé : aucune normalisation, l'utilisateur voit ce qu'il obtient. */
    public static Username parse(String raw) {
        return new Username(raw);
    }

    /** Comme {@link #parse(String)}, mais vide au lieu d'une exception (disponibilité, AUTH-05). */
    public static Optional<Username> tryParse(String raw) {
        return UsernameRule.isWellFormed(raw) ? Optional.of(new Username(raw)) : Optional.empty();
    }
}
