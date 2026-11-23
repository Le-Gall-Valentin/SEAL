package fr.vlegall.seal.common;

/**
 * Codes d'erreur stables de l'API, renvoyés dans la propriété {@code code} des Problem Details et traduits par le
 * client (CDC section 14.6, ADR 0010). Chaque lot ajoute les siens avec le statut que fixe le CDC.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    /** AUTH-55, ADR 0034 : l'action exige un facteur obtenu il y a moins de 10 minutes (mode sudo). */
    REAUTH_REQUIRED(403),
    NOT_FOUND(404),
    RATE_LIMITED(429),
    INTERNAL_ERROR(500),
    /** Requête mal formée au niveau HTTP (405, 406, 413, 415) : la réponse garde le statut choisi par Spring. */
    REQUEST_REJECTED(400),
    // Comptes : statuts fixés par l'ADR 0019.
    /** AUTH-01 : identifiant hors du format imposé. */
    USERNAME_INVALID(400),
    /** AUTH-02 : identifiant déjà pris, même par un compte en attente ou supprimé (AUTH-03). */
    USERNAME_TAKEN(409),
    /** AUTH-02 : identifiant de la liste des noms réservés. */
    USERNAME_RESERVED(422),
    /** AUTH-11 : mot de passe refusé par l'une des règles, longueur maximale comprise. */
    PASSWORD_TOO_WEAK(422),
    /** AUTH-14 : code de vérification erroné ; l'essai est compté. */
    INVALID_CODE(422),
    /** AUTH-14 : aucun code vivant (expiré, consommé, remplacé ou épuisé par les 5 essais). */
    CODE_EXPIRED(422),
    /** AUTH-21 : tout échec de connexion, sans en dire la raison (ADR 0021, point 1). */
    INVALID_CREDENTIALS(401),
    // OAuth : statuts fixés par l'ADR 0032, sur le modèle d'USERNAME_TAKEN.
    /** AUTH-33 : le courriel vérifié du fournisseur appartient déjà à un compte actif ; aucune liaison automatique. */
    OAUTH_EMAIL_IN_USE(409),
    /** AUTH-31 : ce compte du fournisseur est déjà lié à un compte SEAL, ou ce compte SEAL l'est déjà à ce fournisseur. */
    OAUTH_ALREADY_LINKED(409),
    // Cryptographie : statuts fixés par l'ADR 0025, point 5.
    /** Le certificat ou le coffre désigné n'existe pas (KEY-05, KEY-32). */
    KEY_NOT_FOUND(404),
    /** Signature fausse, ou clé publique hors de la courbe (KEY-03, KEY-05). */
    INVALID_SIGNATURE(422),
    /** KEY-32 : la version du coffre n'est pas celle qui suit la version enregistrée. */
    VAULT_VERSION_CONFLICT(409),
    // Amis : statuts fixés par le tableau de la section 10.1.
    /** FRD-02 : aucun compte actif ne porte cet identifiant (risque d'énumération assumé et limité). */
    USER_NOT_FOUND(404),
    /** Section 10.1 : l'identifiant est celui de l'appelant. */
    CANNOT_INVITE_SELF(422),
    /** Section 10.1 : les deux comptes sont déjà amis. */
    ALREADY_FRIENDS(409),
    /** FRD-01 : l'appelant a déjà invité ce compte. */
    REQUEST_ALREADY_PENDING(409),
    /** FRD-01 : ce compte a déjà invité l'appelant ; la propriété {@code requestId} désigne son invitation. */
    INCOMING_REQUEST_EXISTS(409),
    /** FRD-03 : blocage dans un sens ou l'autre, ou invitations fermées, sans dire lequel. */
    CANNOT_INVITE(403);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    /** Statut HTTP de la réponse. */
    public int status() {
        return status;
    }

    /**
     * Code d'une erreur dont on ne connaît que le statut (exception de Spring MVC, dispatch d'erreur du conteneur) :
     * tout 5xx est une erreur interne, un 4xx sans code dédié une requête rejetée (ADR 0010).
     */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 429 -> RATE_LIMITED;
            default -> status >= 500 ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
