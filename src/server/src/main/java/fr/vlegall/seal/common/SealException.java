package fr.vlegall.seal.common;

import java.time.Duration;
import java.util.List;
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
    private final String[] fields;

    /**
     * @param fields champs de la requête en erreur, renvoyés dans la propriété {@code fields} (ADR 0010) ; aucun le plus
     *     souvent
     */
    public SealException(ErrorCode code, String... fields) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
        this.fields = fields.clone();
    }

    public ErrorCode code() {
        return code;
    }

    /** Champs de la requête en erreur ; vide s'il n'y en a pas. */
    public List<String> fields() {
        return List.of(fields);
    }

    /** Délai avant lequel il est inutile de réessayer, renvoyé dans l'en-tête {@code Retry-After} (SEC-10). */
    public Optional<Duration> retryAfter() {
        return Optional.empty();
    }
}
