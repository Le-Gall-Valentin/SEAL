package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.logging.LogValues;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.ws.application.port.in.InboundFrameHandler;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import fr.vlegall.seal.ws.infrastructure.session.BoundSessions;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;

/**
 * TRN-10, TRN-14 : point d'accès {@code /ws}, WebSocket brute en trames texte JSON, sans STOMP. Chaque trame reçoit une
 * réponse au même {@code id} : {@code pong} à {@code ping}, {@code ack} ou {@code error}. Une trame illisible ou
 * binaire ferme la connexion (1008), une trame de plus de 64 Kio aussi (1009). TRN-13 : 5 connexions par compte
 * (4429 au-delà) et 30 trames par 10 secondes et par connexion.
 */
@Component
public final class SealWebSocketHandler extends TextWebSocketHandler {

    /** TRN-13 : 64 Kio au plus par trame, comptés en octets UTF-8. */
    public static final int MAX_FRAME_BYTES = 64 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(SealWebSocketHandler.class);
    private static final String PING = "ping";
    private static final String CONNECTION = "seal.connection";

    private final WireFrames frames;
    private final ConnectionRegistry registry;
    private final RateLimiter limiter;
    private final BoundSessions sessions;
    private final Clock clock;
    private final ConnectionCloser closer;
    private final Map<String, InboundFrameHandler> handlers = new HashMap<>();

    public SealWebSocketHandler(
            WireFrames frames,
            ConnectionRegistry registry,
            RateLimiter limiter,
            BoundSessions sessions,
            Clock clock,
            ConnectionCloser closer,
            List<InboundFrameHandler> handlers) {
        this.frames = frames;
        this.registry = registry;
        this.limiter = limiter;
        this.sessions = sessions;
        this.clock = clock;
        this.closer = closer;
        for (InboundFrameHandler handler : handlers) {
            if (handler.type().equals(PING) || this.handlers.putIfAbsent(handler.type(), handler) != null) {
                throw new IllegalStateException("Deux gestionnaires pour la trame " + handler.type());
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // TRN-13 : tampons de 64 Kio, réglés par connexion (un réglage du conteneur n'existe pas sous MockMvc). Tomcat
        // compte la trame texte en caractères : handleTextMessage recompte en octets UTF-8.
        session.setTextMessageSizeLimit(MAX_FRAME_BYTES);
        session.setBinaryMessageSizeLimit(MAX_FRAME_BYTES);
        UserId user = (UserId) session.getAttributes().get(FullSessionHandshakeInterceptor.USER);
        // TRN-13, A06 : le seau des trames appartient à la connexion, hors de la table partagée du limiteur.
        WebSocketConnection connection = WebSocketConnection.of(
                session,
                limiter.local(RateLimit.WEBSOCKET_FRAMES_PER_CONNECTION, user),
                frames,
                clock.instant(),
                closer);
        session.getAttributes().put(CONNECTION, connection);
        if (!registry.open(connection)) {
            connection.close(CloseReason.TOO_MANY_CONNECTIONS);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.close(connection(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketConnection connection = connection(session);
        connection.heard(clock.instant());
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
            connection.closeWith(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        Optional<WireFrame> read = frames.read(message.getPayload());
        if (read.isEmpty()) {
            connection.closeWith(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WireFrame frame = read.get();
        try {
            connection.frameLimit().consume();
        } catch (RateLimitExceededException limited) {
            connection.send(frames.rateLimited(frame.id(), limited.retryAfter().orElse(Duration.ofSeconds(1))));
            return;
        }
        String answer = answer(connection, frame);
        if (answer != null) {
            connection.send(answer);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        connection(session).closeWith(CloseStatus.POLICY_VIOLATION);
    }

    /** La réponse à la trame, ou {@code null} si la connexion vient d'être fermée. */
    private @Nullable String answer(WebSocketConnection connection, WireFrame frame) {
        if (frame.type().equals(PING)) {
            return frames.frame("pong", frame.id(), frames.empty());
        }
        InboundFrameHandler handler = handlers.get(frame.type());
        if (handler == null) {
            return frames.error(frame.id(), ErrorCode.VALIDATION_FAILED.name());
        }
        // TRN-12, A10 : sur une session finie, aucune trame n'est traitée, sans attendre la ronde. AUTH-52, ADR 0043
        // (point 4) : seule une action de l'utilisateur prolonge la session.
        boolean alive = handler.userAction() ? sessions.userActed(connection) : sessions.alive(connection);
        if (!alive) {
            connection.close(CloseReason.SESSION_ENDED);
            return null;
        }
        try {
            JsonNode result = handler.handle(connection.user(), frame.payload());
            return frames.frame("ack", frame.id(), result);
        } catch (SealException refused) {
            return frames.error(frame.id(), refused.code().name());
        } catch (RuntimeException unexpected) {
            // A10 : l'échec reste fermé ; le détail ne quitte pas le serveur.
            LOG.error("Trame {} non traitée", LogValues.sanitize(frame.type()), unexpected);
            return frames.error(frame.id(), ErrorCode.INTERNAL_ERROR.name());
        }
    }

    private static WebSocketConnection connection(WebSocketSession session) {
        return (WebSocketConnection) session.getAttributes().get(CONNECTION);
    }
}
