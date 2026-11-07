package fr.vlegall.seal.common;

/**
 * Codes d'erreur stables de l'API, renvoyés dans la propriété {@code code} des Problem Details et traduits par le
 * client (CDC section 14.6, ADR 0010). Chaque lot ajoute les siens avec le statut que fixe le CDC.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    RATE_LIMITED(429),
    INTERNAL_ERROR(500),
    /** Requête mal formée au niveau HTTP (405, 406, 413, 415) : la réponse garde le statut choisi par Spring. */
    REQUEST_REJECTED(400);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    /** Statut HTTP de la réponse. */
    public int status() {
        return status;
    }
}
