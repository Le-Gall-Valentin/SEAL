package fr.vlegall.seal.security.journal;

/** Types d'événements de {@code security_events} (SEC-20). Chaque lot ajoute ceux qu'il produit. */
public enum SecurityEventType {
    /** SEC-10 : une limite de débit a été dépassée. */
    RATE_LIMIT_EXCEEDED
}
