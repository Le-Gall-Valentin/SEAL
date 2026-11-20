package fr.vlegall.seal.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("PROTOCOL.md 2.5 : lecture des u64 des vecteurs")
class ProtocolVectorsTest {

    @Test
    @DisplayName("PROTOCOL.md 2.5 : un u64 des vecteurs est une chaîne décimale de 0 à 2^63 - 1, rien d'autre")
    void u64IsADecimalString() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("valid", "42");
        node.put("number", 42);
        node.put("negative", "-5");
        node.put("signed", "+5");
        node.put("fraction", "1.5");
        node.put("leadingZero", "007");
        node.put("overflow", "9223372036854775808");

        assertThat(ProtocolVectors.u64(node, "valid")).isEqualTo(42L);
        for (String field : List.of("number", "negative", "signed", "fraction", "leadingZero", "overflow", "absent")) {
            assertThatThrownBy(() -> ProtocolVectors.u64(node, field))
                    .as(field)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
