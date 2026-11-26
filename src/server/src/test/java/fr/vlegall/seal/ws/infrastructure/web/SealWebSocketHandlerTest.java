package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.ws.application.port.in.InboundFrameHandler;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import fr.vlegall.seal.ws.domain.model.FakeConnection;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
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
    @ValueSource(
            strings = {
                "pong",
                "error",
                "ping",
                "session.revoked",
                "conv.update",
                "friend.request",
                "msg.new",
                "Pas un type",
                ".ack",
                "msg..ack"
            })
    @DisplayName("TRN-14, ADR 0044 : un accusé est ack ou se termine par .ack ; jamais le nom d'une trame du serveur")
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

    @Test
    @DisplayName("TRN-13 : la sixième connexion, en cours de fermeture (4429), ne voit traiter aucune de ses trames")
    void trn13ClosingConnectionIgnoresFrames() throws Exception {
        UserId user = new UserId(UUID.randomUUID());
        ConnectionRegistry registry = new ConnectionRegistry();
        for (int i = 0; i < ConnectionRegistry.MAX_CONNECTIONS_PER_USER; i++) {
            registry.open(new FakeConnection(user, "s" + i, Instant.EPOCH));
        }
        RateLimiter limiter = mock(RateLimiter.class);
        SealWebSocketHandler handler = new SealWebSocketHandler(
                new WireFrames(JsonMapper.builder().build()),
                registry,
                limiter,
                null,
                Clock.systemUTC(),
                new ConnectionCloser(),
                List.of());
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(FullSessionHandshakeInterceptor.USER, user);
        attributes.put(FullSessionHandshakeInterceptor.SESSION, "sixieme");
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("ws-6");

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"ping\",\"id\":\"apres\"}"));

        verify(session, never()).sendMessage(any());
    }
}
