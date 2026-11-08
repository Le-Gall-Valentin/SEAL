package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.Unicode;
import java.util.Objects;

/**
 * Mot de passe en clair, normalisé en NFKC (AUTH-11) : il ne vit que le temps d'un hachage ou d'une vérification.
 * {@link #toString()} ne le révèle pas (SEC-21).
 */
public record Password(String value) {

    public Password {
        Objects.requireNonNull(value, "value");
        if (!Unicode.nfkc(value).equals(value)) {
            throw new IllegalArgumentException("mot de passe non normalisé en NFKC");
        }
    }

    /** Mot de passe saisi, normalisé en NFKC : deux saisies équivalentes donnent le même mot de passe. */
    public static Password fromInput(String raw) {
        return new Password(Unicode.nfkc(raw));
    }

    @Override
    public String toString() {
        return "Password[***]";
    }
}
