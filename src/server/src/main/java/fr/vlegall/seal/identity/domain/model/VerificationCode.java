package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Code de vérification du courriel (AUTH-14) : 6 chiffres, stocké haché en SHA-256, comparé en temps constant.
 * {@link #toString()} ne le révèle pas (SEC-21).
 */
public record VerificationCode(String value) {

    public static final int LENGTH = 6;

    /** Chiffres ASCII seulement : {@code [0-9]} ne reconnaît pas les chiffres d'autres écritures. */
    private static final Pattern FORMAT = Pattern.compile("[0-9]{6}");

    public VerificationCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("code de vérification mal formé");
        }
    }

    public static VerificationCode generate(RandomDigits random) {
        return new VerificationCode(random.next(LENGTH));
    }

    /** Code saisi ; vide s'il ne compte pas exactement 6 chiffres ASCII. */
    public static Optional<VerificationCode> parse(String raw) {
        return FORMAT.matcher(raw).matches() ? Optional.of(new VerificationCode(raw)) : Optional.empty();
    }

    /** SHA-256 des 6 chiffres en ASCII, seule forme stockée (DATA-04). */
    public byte[] hash() {
        return Digests.sha256(value.getBytes(StandardCharsets.US_ASCII));
    }

    /** Vrai si ce code est celui dont {@code storedHash} est l'empreinte, comparée en temps constant (A04). */
    public boolean matches(byte[] storedHash) {
        return Digests.equal(hash(), storedHash);
    }

    @Override
    public String toString() {
        return "VerificationCode[******]";
    }
}
