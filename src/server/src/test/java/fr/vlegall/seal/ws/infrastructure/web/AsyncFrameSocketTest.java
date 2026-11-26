package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

class AsyncFrameSocketTest {

    @Test
    @DisplayName(
            "TRN-13, ADR 0044 (décision 10) : l'écriture passe par l'API asynchrone, 10 s au plus, sans jamais bloquer")
    void trn13WritesThroughTheAsyncApi() throws Exception {
        StandardWebSocketSession session = mock(StandardWebSocketSession.class);
        Session endpoint = mock(Session.class);
        RemoteEndpoint.Async remote = mock(RemoteEndpoint.Async.class);
        when(session.getNativeSession()).thenReturn(endpoint);
        when(endpoint.getAsyncRemote()).thenReturn(remote);
        List<Boolean> results = new ArrayList<>();
        AsyncFrameSocket socket = new AsyncFrameSocket(session);

        socket.sendText("trame", results::add);

        verify(remote).setSendTimeout(10_000);
        ArgumentCaptor<SendHandler> handler = ArgumentCaptor.forClass(SendHandler.class);
        verify(remote).sendText(eq("trame"), handler.capture());
        verify(session, never()).sendMessage(any());
        assertThat(results).as("rien avant le rappel de Tomcat").isEmpty();
        handler.getValue().onResult(new SendResult(endpoint));
        handler.getValue().onResult(new SendResult(endpoint, new IOException("client trop lent")));
        assertThat(results).containsExactly(true, false);
    }
}
