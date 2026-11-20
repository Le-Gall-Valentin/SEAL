package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.DisplayNameRule;
import fr.vlegall.seal.common.Unicode;
import java.util.Objects;
import java.util.Optional;

/** Nom affiché (AUTH-10), modifiable ensuite : 1 à 50 points de code après NFC, sans caractère piégé (SEC-01). */
public record DisplayName(String value) {

    /** AUTH-10, en points de code, comme {@code char_length} de PostgreSQL. */
    public static final int MAX_LENGTH = DisplayNameRule.MAX_LENGTH;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (!DisplayNameRule.isValid(value)) {
            throw new IllegalArgumentException("nom affiché invalide");
        }
    }

    /** Nom saisi, normalisé en NFC ; vide s'il enfreint une règle. */
    public static Optional<DisplayName> parse(String raw) {
        String normalized = Unicode.nfc(raw);
        return DisplayNameRule.isValid(normalized) ? Optional.of(new DisplayName(normalized)) : Optional.empty();
    }
}
