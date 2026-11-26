package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.application.port.in.InboundFrameHandler;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SealWebSocketHandlerTest {

    private static InboundFrameHandler handler(String type, String reply) {
        return new InboundFrameHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public String replyType() {
                return reply;
            }

            @Override
            public boolean userAction() {
                return false;
            }

            @Override
            public JsonNode handle(UserId user, JsonNode payload) {
                return payload;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"pong", "error", "ping", "session.revoked", "Pas un type"})
    @DisplayName("TRN-14, ADR 0044 : un accusé ne peut porter ni un nom réservé ni un nom mal formé")
    void trn14ReservedOrMalformedReplyIsRefused(String reply) {
        assertThatThrownBy(() -> new SealWebSocketHandler(
                        new WireFrames(JsonMapper.builder().build()),
                        new ConnectionRegistry(),
                        null,
                        null,
                        Clock.systemUTC(),
                        new ConnectionCloser(),
                        List.of(handler("test.x", reply))))
                .isInstanceOf(IllegalStateException.class);
    }
}
