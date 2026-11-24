package fr.vlegall.seal.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CNV-01 : clé d'une conversation directe")
class DirectKeyTest {

    private final UserId high = new UserId(UUID.fromString("90000000-0000-7000-8000-00000000000a"));
    private final UserId low = new UserId(UUID.fromString("10000000-0000-7000-8000-00000000000b"));

    @Test
    @DisplayName("CNV-01 : les deux UUID triés en ordre non signé, la même clé dans les deux sens")
    void cnv01SameKeyBothWaysInUnsignedOrder() {
        DirectKey key = DirectKey.of(high, low);

        assertThat(key).isEqualTo(DirectKey.of(low, high));
        assertThat(key.value()).isEqualTo("10000000-0000-7000-8000-00000000000b:90000000-0000-7000-8000-00000000000a");
    }

    @Test
    @DisplayName("CNV-01 : une directe relie deux comptes distincts")
    void cnv01SameUserTwiceIsRefused() {
        assertThatThrownBy(() -> DirectKey.of(low, low)).isInstanceOf(IllegalArgumentException.class);
    }
}
