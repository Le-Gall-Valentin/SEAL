package fr.vlegall.seal.common;

import java.util.Objects;

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
}
