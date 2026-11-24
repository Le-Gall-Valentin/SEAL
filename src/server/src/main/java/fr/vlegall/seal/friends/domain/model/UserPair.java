package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * FRD-01 : deux comptes distincts, sans sens, rangés comme {@code friendships} ({@code user_low < user_high}) et
 * l'index {@code one_pending_request_per_pair}. L'ordre est celui de PostgreSQL, octet par octet non signé : celui de
 * {@link UUID#compareTo} compare des {@code long} signés et ne coïnciderait pas.
 */
public record UserPair(UserId low, UserId high) {

    public UserPair {
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
        if (UserId.POSTGRES_ORDER.compare(low, high) >= 0) {
            throw new IllegalArgumentException("une paire range deux comptes distincts, le plus petit d'abord");
        }
    }

    /** Paire de deux comptes distincts, dans n'importe quel ordre. */
    public static UserPair of(UserId a, UserId b) {
        return UserId.POSTGRES_ORDER.compare(a, b) <= 0 ? new UserPair(a, b) : new UserPair(b, a);
    }

    /** L'autre membre de la paire. */
    public UserId other(UserId member) {
        if (member.equals(low)) {
            return high;
        }
        if (member.equals(high)) {
            return low;
        }
        throw new IllegalArgumentException("compte étranger à la paire");
    }
}
