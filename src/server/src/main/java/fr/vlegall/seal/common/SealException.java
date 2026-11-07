package fr.vlegall.seal.common;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Erreur métier de SEAL, convertie en Problem Details avec son {@link ErrorCode}. Sans dépendance à Spring, pour que
 * les couches {@code domain} puissent l'étendre. Le message reste dans le journal du serveur : la réponse ne porte que
 * le code (A02).
 */
public class SealException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public SealException(ErrorCode code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /** Délai avant lequel il est inutile de réessayer, renvoyé dans l'en-tête {@code Retry-After} (SEC-10). */
    public Optional<Duration> retryAfter() {
        return Optional.empty();
    }
}
