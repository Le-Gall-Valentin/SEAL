package fr.vlegall.seal.friends.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ADR 0037, point 3 : page d'une liste lue avec une ligne de plus que la limite")
class PageTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    @DisplayName("ADR 0037 : une ligne de trop annonce la suite, après le dernier élément gardé")
    void adr0037ExtraRowAnnouncesNextPage() {
        Page<UUID> page = Page.cut(List.of(a, b, c), 2, id -> id);

        assertThat(page.items()).containsExactly(a, b);
        assertThat(page.next()).contains(b);
    }

    @Test
    @DisplayName("ADR 0037 : sans ligne de trop, c'est la dernière page")
    void adr0037NoExtraRowIsTheLastPage() {
        assertThat(Page.cut(List.of(a, b), 2, id -> id).next()).isEmpty();
        assertThat(Page.cut(List.<UUID>of(), 2, id -> id).items()).isEmpty();
    }

    @Test
    @DisplayName("ADR 0037 : filtrer les éléments garde le curseur de la page brute")
    void adr0037FilteringKeepsTheRawCursor() {
        Page<UUID> page = Page.cut(List.of(a, b, c), 2, id -> id);

        Page<String> filtered = page.withItems(List.of("seul"));

        assertThat(filtered.items()).containsExactly("seul");
        assertThat(filtered.next()).contains(b);
    }
}
