package fr.vlegall.seal.common.web;

import fr.vlegall.seal.common.Page;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** ADR 0037 (point 3), ADR 0039 (point 6) : {@code {"items": [...], "next": "<identifiant>" | null}}. */
public record PageView<T>(List<T> items, @Nullable UUID next) {

    public PageView {
        items = List.copyOf(items);
    }

    public static <S, T> PageView<T> of(Page<S> page, Function<S, T> view) {
        return new PageView<>(
                page.items().stream().map(view).toList(), page.next().orElse(null));
    }
}
