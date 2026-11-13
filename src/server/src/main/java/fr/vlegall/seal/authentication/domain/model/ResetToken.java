package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.crypto.Digests;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Jeton d'un lien de réinitialisation (AUTH-70, ADR 0022) : 32 octets aléatoires, écrits en base64url sans remplissage
 * (43 caractères). Seul le SHA-256 des 32 octets est conservé ; le jeton lui-même ne quitte le serveur que dans le
 * courriel, et n'est jamais journalisé (SEC-21).
 */
public final class ResetToken {

    /** AUTH-70 : 256 bits. */
    public static final int BYTES = 32;

    /** Longueur en base64url sans remplissage. */
    public static final int LENGTH = 43;

    /** AUTH-70 : un lien vaut 30 minutes. */
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] bytes;
    private final String value;

    private ResetToken(byte[] bytes) {
        this.bytes = bytes.clone();
        this.value = ENCODER.encodeToString(bytes);
    }

    /** Jeton fait de {@code random}, tiré par un générateur cryptographique, de 32 octets exactement. */
    public static ResetToken of(byte[] random) {
        if (random.length != BYTES) {
            throw new IllegalArgumentException("un jeton compte " + BYTES + " octets, pas " + random.length);
        }
        return new ResetToken(random);
    }

    /**
     * Jeton lu dans un lien, ou vide si la saisie n'en est pas exactement un : 43 caractères base64url, sans remplissage,
     * sous la seule forme qu'en donne l'encodage (les 2 derniers bits nuls). Vérification linéaire, sans exception.
     */
    public static Optional<ResetToken> parse(String input) {
        if (input.length() != LENGTH || !input.chars().allMatch(ResetToken::isBase64Url)) {
            return Optional.empty();
        }
        ResetToken token = new ResetToken(Base64.getUrlDecoder().decode(input));
        return token.value.equals(input) ? Optional.of(token) : Optional.empty();
    }

    private static boolean isBase64Url(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    /** Le jeton tel qu'il figure dans le lien. */
    public String value() {
        return value;
    }

    /** SHA-256 des 32 octets, seule forme conservée en base ({@code password_reset_tokens.token_hash}). */
    public byte[] hash() {
        return Digests.sha256(bytes);
    }

    /** SEC-21 : le jeton n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "ResetToken[***]";
    }
}
