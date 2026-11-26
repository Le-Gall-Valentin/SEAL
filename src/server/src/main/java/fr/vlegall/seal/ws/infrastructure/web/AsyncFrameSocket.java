package fr.vlegall.seal.ws.infrastructure.web;

import java.io.IOException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

/**
 * TRN-13, ADR 0044 (décision 10) : écriture par l'API asynchrone de Jakarta WebSocket. Aucun fil n'attend le client :
 * Tomcat rappelle à la fin de l'écriture, ou en échec après 10 secondes. Hors de Tomcat, l'écriture est synchrone.
 */
final class AsyncFrameSocket implements FrameSocket {

    /** Délai d'une écriture, comme le tolérait le décorateur de Spring. */
    static final long SEND_TIMEOUT_MS = 10_000;
    /** Délai de l'écriture bloquante de la trame de fermeture (Tomcat : 20 s par défaut). */
    static final String BLOCKING_SEND_TIMEOUT = "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

    private static final Logger LOG = LoggerFactory.getLogger(AsyncFrameSocket.class);

    /** Délai d'écriture de la trame de fermeture quand la fermeture ne peut pas attendre. */
    static final long QUICK_CLOSE_TIMEOUT_MS = 100;

    private final WebSocketSession session;
    private final jakarta.websocket.@Nullable Session endpoint;
    private final jakarta.websocket.RemoteEndpoint.@Nullable Async remote;

    AsyncFrameSocket(WebSocketSession session) {
        this.session = session;
        if (session instanceof NativeWebSocketSession nativeSession
                && nativeSession.getNativeSession() instanceof jakarta.websocket.Session endpoint) {
            endpoint.getUserProperties().put(BLOCKING_SEND_TIMEOUT, SEND_TIMEOUT_MS);
            this.endpoint = endpoint;
            this.remote = endpoint.getAsyncRemote();
            this.remote.setSendTimeout(SEND_TIMEOUT_MS);
        } else {
            this.endpoint = null;
            this.remote = null;
        }
    }

    @Override
    public void sendText(String text, Consumer<Boolean> done) {
        if (remote != null) {
            remote.sendText(text, result -> done.accept(result.isOK()));
            return;
        }
        boolean written;
        try {
            session.sendMessage(new TextMessage(text));
            written = true;
        } catch (IOException | RuntimeException failed) {
            written = false;
        }
        done.accept(written);
    }

    @Override
    public void closeQuickly(CloseStatus status) {
        if (endpoint != null) {
            endpoint.getUserProperties().put(BLOCKING_SEND_TIMEOUT, QUICK_CLOSE_TIMEOUT_MS);
        }
        close(status);
    }

    @Override
    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException gone) {
            LOG.debug("Connexion déjà fermée ({})", gone.getClass().getSimpleName());
        }
    }
}
