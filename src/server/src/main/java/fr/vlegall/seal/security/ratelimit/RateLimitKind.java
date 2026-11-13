package fr.vlegall.seal.security.ratelimit;

/** Nature de la clé d'une limite, d'après la colonne « Clé » de la section 15.2 du CDC (ADR 0013, point 2). */
public enum RateLimitKind {
    /**
     * Adresse IP, IPv6 agrégée à son préfixe /64 (ADR 0021, point 6) : la clé est journalisée ainsi, et la colonne
     * {@code ip} du journal porte l'adresse entière.
     */
    IP,
    /** Identifiant saisi : jamais journalisé, ce pourrait être un mot de passe (SEC-21, ADR 0021, mise à jour). */
    ENTERED,
    /** Courriel saisi : masqué s'il en a la forme, {@code ***} sinon. */
    EMAIL,
    /** Utilisateur : recopié dans {@code security_events.user_id}. */
    USER
}
