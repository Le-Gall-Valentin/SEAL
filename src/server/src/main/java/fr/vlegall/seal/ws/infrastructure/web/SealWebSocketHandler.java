package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.logging.LogValues;
import fr.vlegall.seal.ws.application.port.in.InboundFrameHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * binaire ferme la connexion (1008), une trame de plus de 64 Kio aussi (1009, TRN-13).
 */
@Component
public final class SealWebSocketHandler extends TextWebSocketHandler {

    /** TRN-13 : 64 Kio au plus par trame, comptés en octets UTF-8. */
    public static final int MAX_FRAME_BYTES = 64 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(SealWebSocketHandler.class);
    private static final String PING = "ping";

    private final WireFrames frames;
    private final Map<String, InboundFrameHandler> handlers = new HashMap<>();

    public SealWebSocketHandler(WireFrames frames, List<InboundFrameHandler> handlers) {
        this.frames = frames;
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
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        Optional<WireFrame> read = frames.read(message.getPayload());
        if (read.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WireFrame frame = read.get();
        if (frame.type().equals(PING)) {
            send(session, frames.frame("pong", frame.id(), frames.empty()));
            return;
        }
        InboundFrameHandler handler = handlers.get(frame.type());
        if (handler == null) {
            send(session, frames.error(frame.id(), ErrorCode.VALIDATION_FAILED.name()));
            return;
        }
        send(session, answer(handler, user(session), frame));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException alreadyGone) {
            LOG.debug("Connexion déjà fermée", alreadyGone);
        }
    }

    private String answer(InboundFrameHandler handler, UserId user, WireFrame frame) {
        try {
            JsonNode result = handler.handle(user, frame.payload());
            return frames.frame("ack", frame.id(), result);
        } catch (SealException refused) {
            return frames.error(frame.id(), refused.code().name());
        } catch (RuntimeException unexpected) {
            // A10 : l'échec reste fermé ; le détail ne quitte pas le serveur.
            LOG.error("Trame {} non traitée", LogValues.sanitize(frame.type()), unexpected);
            return frames.error(frame.id(), ErrorCode.INTERNAL_ERROR.name());
        }
    }

    private static UserId user(WebSocketSession session) {
        return (UserId) session.getAttributes().get(FullSessionHandshakeInterceptor.USER);
    }

    private static void send(WebSocketSession session, String text) throws IOException {
        session.sendMessage(new TextMessage(text));
    }
}
