package fr.vlegall.seal.identity.domain.model;

import java.util.Objects;
import java.util.Optional;

/** Identifiant d'un compte, définitif (AUTH-03) : la forme est vérifiée à la construction (AUTH-01). */
public record Username(String value) {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 20;

    public Username {
        Objects.requireNonNull(value, "value");
        if (!isWellFormed(value)) {
            throw new IdentityException.UsernameInvalid();
        }
    }

    /** Identifiant saisi, accepté tel quel ou refusé : aucune normalisation, l'utilisateur voit ce qu'il obtient. */
    public static Username parse(String raw) {
        return new Username(raw);
    }

    /** Comme {@link #parse(String)}, mais vide au lieu d'une exception (disponibilité, AUTH-05). */
    public static Optional<Username> tryParse(String raw) {
        return isWellFormed(raw) ? Optional.of(new Username(raw)) : Optional.empty();
    }

    /**
     * AUTH-01, {@code ^(?=.{3,20}$)[a-z0-9]+(?:[._][a-z0-9]+)*$} : 3 à 20 caractères, minuscules et chiffres,
     * {@code .} et {@code _} isolés entre deux alphanumériques. Vérification linéaire, sans expression régulière ;
     * {@code UsernameTest} la compare à l'expression du CDC.
     */
    private static boolean isWellFormed(String value) {
        int length = value.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        boolean previousWasSeparator = true;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean separator = c == '.' || c == '_';
            if (separator ? previousWasSeparator : !((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
            previousWasSeparator = separator;
        }
        return !previousWasSeparator;
    }
}
