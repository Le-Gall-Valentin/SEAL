package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.LocalRateLimit;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.Connection;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Une connexion de {@code /ws}, sûre entre fils d'exécution : les réponses et les trames d'événements (tâche 5) partent
 * de fils différents. Un envoi lent au-delà de 10 secondes, ou qui accumule plus de 512 Kio, ferme la connexion.
 */
final class WebSocketConnection implements Connection {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final WebSocketSession session;
    private final UserId user;
    private final String sessionId;
    private final LocalRateLimit frameLimit;

    WebSocketConnection(WebSocketSession session, LocalRateLimit frameLimit) {
        this.frameLimit = frameLimit;
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        this.user = (UserId) session.getAttributes().get(FullSessionHandshakeInterceptor.USER);
        this.sessionId = (String) session.getAttributes().get(FullSessionHandshakeInterceptor.SESSION);
    }

    /** TRN-13 : seau des trames de cette connexion, libéré avec elle. */
    LocalRateLimit frameLimit() {
        return frameLimit;
    }

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public UserId user() {
        return user;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public void send(String text) {
        try {
            session.sendMessage(new TextMessage(text));
        } catch (IOException | IllegalStateException gone) {
            LOG.debug("Trame non envoyée, connexion fermée", gone);
        }
    }

    @Override
    public void close(CloseReason reason) {
        closeWith(new CloseStatus(reason.code()));
    }

    void closeWith(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException gone) {
            LOG.debug("Connexion déjà fermée", gone);
        }
    }
}
