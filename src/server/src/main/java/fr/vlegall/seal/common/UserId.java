package fr.vlegall.seal.common;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un utilisateur, seul type partagé entre les contextes (ADR 0008). Sérialisable : il voyage dans la
 * session, que Spring Session JDBC enregistre en base (AUTH-50).
 */
public record UserId(UUID value) implements Serializable {

    private static final long serialVersionUID = 1L;

    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
