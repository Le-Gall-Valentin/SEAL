package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class WebSocketConnectionTest {

    private final WebSocketSession raw = mock(WebSocketSession.class);
    private final WebSocketConnection connection;

    WebSocketConnectionTest() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(FullSessionHandshakeInterceptor.USER, new UserId(UUID.randomUUID()));
        attributes.put(FullSessionHandshakeInterceptor.SESSION, "session");
        when(raw.getAttributes()).thenReturn(attributes);
        when(raw.getId()).thenReturn("ws-1");
        when(raw.isOpen()).thenReturn(true);
        connection = new WebSocketConnection(
                raw, () -> {}, new WireFrames(JsonMapper.builder().build()), Instant.EPOCH);
    }

    @Test
    @DisplayName("TRN-13, ADR 0044 (décision 10) : un envoi refusé ne remonte pas ; la connexion se ferme en 1011")
    void trn13FailedSendClosesTheConnection() throws Exception {
        doThrow(new IllegalArgumentException("encodage")).when(raw).sendMessage(any());

        assertThatNoException().isThrownBy(() -> connection.send("{}"));

        verify(raw).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("TRN-12 : session.revoked part avant la fermeture 4401")
    void trn12RevokedFrameThenClose() throws Exception {
        connection.close(CloseReason.SESSION_ENDED);

        InOrder order = inOrder(raw);
        order.verify(raw).sendMessage(any(TextMessage.class));
        order.verify(raw).close(new CloseStatus(4401));
    }
}
