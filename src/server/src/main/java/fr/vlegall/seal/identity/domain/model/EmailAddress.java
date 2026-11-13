package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.EmailFormat;
import fr.vlegall.seal.common.Unicode;
import java.util.Objects;
import java.util.Optional;

/**
 * Adresse électronique d'un compte, normalisée en NFC (SEC-01) et gardée telle que saisie : PostgreSQL la compare
 * sans tenir compte de la casse ({@code citext}). {@link #toString()} ne la révèle pas (SEC-21).
 */
public record EmailAddress(String value) {

    /** Longueur maximale d'une adresse (RFC 5321, chemin complet). */
    public static final int MAX_LENGTH = EmailFormat.MAX_LENGTH;

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (!EmailFormat.isValid(value)) {
            throw new IllegalArgumentException("adresse invalide");
        }
    }

    /** Adresse saisie, normalisée en NFC ; vide si elle est mal formée. */
    public static Optional<EmailAddress> parse(String raw) {
        String normalized = Unicode.nfc(raw);
        return EmailFormat.isValid(normalized) ? Optional.of(new EmailAddress(normalized)) : Optional.empty();
    }

    /** Partie locale avant le premier {@code +} ou {@code @} (AUTH-04, étape 1 ; AUTH-11, ADR 0019). */
    public String localPart() {
        int at = value.indexOf('@');
        int plus = value.indexOf('+');
        return value.substring(0, plus >= 0 && plus < at ? plus : at);
    }

    @Override
    public String toString() {
        return "EmailAddress[***]";
    }
}
