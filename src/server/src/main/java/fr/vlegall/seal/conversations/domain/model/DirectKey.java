package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;

/**
 * CNV-01 : colonne {@code direct_key}, les deux UUID triés en ordre non signé ({@link UserId#POSTGRES_ORDER}) et séparés
 * par {@code :} ; la même clé dans les deux sens, sous index unique.
 */
public record DirectKey(String value) {

    public DirectKey {
        Objects.requireNonNull(value, "value");
    }

    public static DirectKey of(UserId a, UserId b) {
        int order = UserId.POSTGRES_ORDER.compare(a, b);
        if (order == 0) {
            throw new IllegalArgumentException("une directe relie deux comptes distincts");
        }
        UserId low = order < 0 ? a : b;
        UserId high = order < 0 ? b : a;
        return new DirectKey(low.value() + ":" + high.value());
    }
}
