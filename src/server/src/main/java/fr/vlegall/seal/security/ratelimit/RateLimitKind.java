package fr.vlegall.seal.security.ratelimit;

/** Nature de la clé d'une limite, d'après la colonne « Clé » de la section 15.2 du CDC (ADR 0013, point 2). */
public enum RateLimitKind {
    /** Adresse IP : gardée telle quelle dans le journal, qui la porte aussi dans sa colonne {@code ip}. */
    IP,
    /** Identifiant saisi : journalisé seulement s'il a la forme d'un identifiant ou d'un courriel (masqué). */
    ENTERED,
    /** Courriel saisi : masqué s'il en a la forme, {@code ***} sinon. */
    EMAIL,
    /** Utilisateur : recopié dans {@code security_events.user_id}. */
    USER
}
