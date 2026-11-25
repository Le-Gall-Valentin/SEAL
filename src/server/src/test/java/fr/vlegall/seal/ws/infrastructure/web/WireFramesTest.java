package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class WireFramesTest {

    private final WireFrames frames = new WireFrames(JsonMapper.builder().build());

    @Test
    @DisplayName("TRN-14 : une trame { type, id, payload } se lit telle quelle")
    void trn14ReadsAFrame() {
        Optional<WireFrame> frame = frames.read("{\"type\":\"ping\",\"id\":\"a-1_B\",\"payload\":{\"n\":1}}");

        assertThat(frame).hasValueSatisfying(read -> {
            assertThat(read.type()).isEqualTo("ping");
            assertThat(read.id()).isEqualTo("a-1_B");
            assertThat(read.payload().get("n").asInt()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("TRN-14, ADR 0043 : sans charge, la trame porte un objet vide")
    void trn14MissingPayloadIsEmpty() {
        assertThat(frames.read("{\"type\":\"ping\",\"id\":\"1\"}"))
                .hasValueSatisfying(read -> assertThat(read.payload().isEmpty()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "pas du JSON",
                "[]",
                "{\"type\":\"ping\"}",
                "{\"id\":\"1\"}",
                "{\"type\":\"ping\",\"id\":\"1\",\"extra\":true}",
                "{\"type\":\"ping\",\"id\":\"\"}",
                "{\"type\":\"ping\",\"id\":\"a b\"}",
                "{\"type\":\"ping\",\"id\":\"12345678901234567890123456789012345678901234567890123456789012345\"}",
                "{\"type\":1,\"id\":\"1\"}",
                "{\"type\":\"ping\",\"id\":1}",
                "{\"type\":\"ping\",\"id\":\"1\",\"payload\":[]}",
                "{\"type\":\"ping\",\"id\":\"1\",\"payload\":\"x\"}",
                "{\"type\":\"msg.\",\"id\":\"1\"}",
                "{\"type\":\".msg\",\"id\":\"1\"}",
                "{\"type\":\"msg..send\",\"id\":\"1\"}",
                "{\"type\":\"Msg\",\"id\":\"1\"}"
            })
    @DisplayName("TRN-14, A10 : une trame illisible, incomplète ou à champ inconnu est refusée")
    void trn14RefusesMalformedFrames(String text) {
        assertThat(frames.read(text)).isEmpty();
    }

    @Test
    @DisplayName("TRN-14 : réponses ack, error et pong au même id")
    void trn14WritesResponses() {
        assertThat(frames.frame("pong", "7", JsonMapper.builder().build().createObjectNode()))
                .isEqualTo("{\"type\":\"pong\",\"id\":\"7\",\"payload\":{}}");
        assertThat(frames.error("7", "VALIDATION_FAILED"))
                .isEqualTo("{\"type\":\"error\",\"id\":\"7\",\"payload\":{\"code\":\"VALIDATION_FAILED\"}}");
    }
}
