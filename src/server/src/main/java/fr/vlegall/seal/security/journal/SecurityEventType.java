package fr.vlegall.seal.security.journal;

/** Types d'événements de {@code security_events} (SEC-20). Chaque lot ajoute ceux qu'il produit. */
public enum SecurityEventType {
    /** SEC-10 : une limite de débit a été dépassée. */
    RATE_LIMIT_EXCEEDED,
    /** SEC-20 : inscription classique (AUTH-10). */
    ACCOUNT_REGISTERED,
    /** SEC-20 : courriel d'un compte remplacé (AUTH-17). */
    EMAIL_CHANGED
}
