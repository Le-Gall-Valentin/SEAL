package fr.vlegall.seal.security.journal;

/** Types d'événements de {@code security_events} (SEC-20). Chaque lot ajoute ceux qu'il produit. */
public enum SecurityEventType {
    /** SEC-10 : une limite de débit a été dépassée. */
    RATE_LIMIT_EXCEEDED,
    /** SEC-20 : inscription classique (AUTH-10). */
    ACCOUNT_REGISTERED,
    /** SEC-20 : courriel d'un compte remplacé (AUTH-17). */
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
    PASSWORD_RESET
}
