package fr.vlegall.seal.security.journal;

/** Types d'événements de {@code security_events} (SEC-20). Chaque lot ajoute ceux qu'il produit. */
public enum SecurityEventType {
    /** SEC-10 : une limite de débit a été dépassée. */
    RATE_LIMIT_EXCEEDED,
    /** SEC-20 : inscription, classique (AUTH-10) ou OAuth (AUTH-32). */
    ACCOUNT_REGISTERED,
    /** SEC-20 : courriel d'un compte remplacé, en attente (AUTH-17) ou actif (ACC-02). */
    EMAIL_CHANGED,
    /** SEC-20 : connexion réussie (AUTH-20). */
    LOGIN_SUCCEEDED,
    /** SEC-20 : connexion refusée (AUTH-21) ; la saisie est filtrée (SEC-21). */
    LOGIN_FAILED,
    /** SEC-20 : déconnexion (AUTH-53). */
    LOGOUT,
    /** SEC-20 : sessions d'un compte révoquées (AUTH-16, AUTH-70), avec leur nombre. */
    SESSION_REVOKED,
    /** SEC-20 : mot de passe réinitialisé par lien (AUTH-70). */
    PASSWORD_RESET,
    /** SEC-20 : mot de passe changé depuis les paramètres (ACC-01). */
    PASSWORD_CHANGED,
    /** SEC-20 : clé d'identité publiée et certifiée (KEY-04), avec le numéro de série du certificat. */
    IDENTITY_KEY_PUBLISHED,
    /**
     * SEC-20 : identité réinitialisée (KEY-34, rotation de clé), avec les numéros de série du nouveau certificat et
     * de l'ancien, révoqué dans la même transaction.
     */
    IDENTITY_KEY_RESET,
    /** SEC-20 : sous-clé de chiffrement publiée (KEY-05), avec son ekId. */
    SUBKEY_PUBLISHED,
    /** SEC-20 : compte lié à un fournisseur OAuth (AUTH-31), avec le fournisseur. */
    OAUTH_LINKED
}
