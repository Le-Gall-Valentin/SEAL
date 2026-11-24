package fr.vlegall.seal.common;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un utilisateur, seul type partagé entre les contextes (ADR 0008). Sérialisable : il voyage dans la
 * session, que Spring Session JDBC enregistre en base (AUTH-50).
 */
public record UserId(UUID value) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Ordre des {@code uuid} de PostgreSQL, octet par octet non signé : celui de {@link UUID#compareTo} compare des
     * {@code long} signés et ne coïnciderait pas (paires d'amis, clé d'une directe, succession d'un administrateur).
     */
    public static final Comparator<UserId> POSTGRES_ORDER = Comparator.comparing(
                    (UserId id) -> id.value().getMostSignificantBits(), Long::compareUnsigned)
            .thenComparing(id -> id.value().getLeastSignificantBits(), Long::compareUnsigned);

    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
