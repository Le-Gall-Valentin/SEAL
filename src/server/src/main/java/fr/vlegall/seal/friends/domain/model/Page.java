package fr.vlegall.seal.friends.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * ADR 0037, point 3 : page d'une liste paginée par curseur. {@code next} est l'identifiant à passer en {@code after}
 * pour la page suivante, vide sur la dernière.
 */
public record Page<T>(List<T> items, Optional<UUID> next) {

    /** Taille maximale d'une page (convention d'API, {@code limit} de 50 au plus). */
    public static final int MAX_LIMIT = 50;

    public Page {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
    }

    /**
     * Page tirée d'une lecture de {@code limit + 1} lignes : la ligne de trop annonce la suite, qui reprend après le
     * dernier élément gardé.
     */
    public static <T> Page<T> cut(List<T> rows, int limit, Function<T, UUID> cursor) {
        if (rows.size() <= limit) {
            return new Page<>(rows, Optional.empty());
        }
        List<T> kept = rows.subList(0, limit);
        return new Page<>(kept, Optional.of(cursor.apply(kept.getLast())));
    }

    /**
     * Mêmes curseurs, autres éléments : une page dont on a retiré les comptes inactifs garde la suite de la page brute,
     * même plus courte que la limite, voire vide.
     */
    public <R> Page<R> withItems(List<R> replaced) {
        return new Page<>(replaced, next);
    }
}
