package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.LocalRateLimit;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.Connection;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Une connexion de {@code /ws}, sûre entre fils d'exécution : les réponses et les trames d'événements (tâche 5) partent
 * de fils différents. Un envoi lent au-delà de 10 secondes, ou qui accumule plus de 512 Kio, ferme la connexion.
 */
final class WebSocketConnection implements Connection {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;
    /** ADR 0044 (décision 10) : un envoi bloqué abandonne au bout de 10 s, comme le décorateur (Tomcat : 20 s). */
    static final String BLOCKING_SEND_TIMEOUT = "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";
    /** TRN-12 : attente au plus de la remise de session.revoked avant la fermeture. */
    private static final long REVOKED_FLUSH_WAIT_MS = 2_000;

    private final ConcurrentWebSocketSessionDecorator session;
    private final UserId user;
    private final LocalRateLimit frameLimit;
    private final WireFrames frames;
    private volatile String sessionId;
    private volatile @Nullable String formerSessionId;
    private volatile Instant lastHeardAt;

    WebSocketConnection(WebSocketSession session, LocalRateLimit frameLimit, WireFrames frames, Instant openedAt) {
        this.frameLimit = frameLimit;
        this.frames = frames;
        this.lastHeardAt = openedAt;
        if (session instanceof NativeWebSocketSession nativeSession
                && nativeSession.getNativeSession() instanceof jakarta.websocket.Session endpoint) {
            endpoint.getUserProperties().put(BLOCKING_SEND_TIMEOUT, (long) SEND_TIME_LIMIT_MS);
        }
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
    public Optional<String> formerSessionId() {
        return Optional.ofNullable(formerSessionId);
    }

    @Override
    public void follow(String renamedSessionId) {
        formerSessionId = sessionId;
        sessionId = renamedSessionId;
    }

    @Override
    public Instant lastHeardAt() {
        return lastHeardAt;
    }

    /** TRN-13 : une trame vient d'arriver. */
    void heard(Instant at) {
        lastHeardAt = at;
    }

    @Override
    public void send(String text) {
        try {
            session.sendMessage(new TextMessage(text));
        } catch (IOException | RuntimeException failed) {
            // TRN-13, ADR 0044 (décision 10) : un client trop lent (plus de 10 s ou de 512 Kio en attente) ou un envoi
            // impossible ferme la connexion en 1011 ; l'échec ne remonte jamais à l'appelant.
            LOG.debug(
                    "Trame non envoyée ({}) : connexion fermée",
                    failed.getClass().getSimpleName());
            abort();
        }
    }

    @Override
    public void close(CloseReason reason) {
        if (reason == CloseReason.SESSION_ENDED) {
            // TRN-12 : session.revoked, puis 4401 ; l'identifiant d'une trame du serveur est choisi par lui.
            send(frames.frame("session.revoked", UUID.randomUUID().toString(), frames.empty()));
            awaitFlush();
        }
        closeWith(new CloseStatus(reason.code()));
    }

    /**
     * TRN-12 : si un autre fil vide le tampon, la fermeture l'interromprait avant session.revoked ; on attend qu'il
     * soit vide, 2 secondes au plus.
     */
    private void awaitFlush() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REVOKED_FLUSH_WAIT_MS);
        while ((session.getBufferSize() > 0 || session.getTimeSinceSendStarted() > 0) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 1011, sur la session elle-même : le décorateur remplacerait le code après un dépassement de ses limites. */
    private void abort() {
        try {
            session.getDelegate().close(CloseStatus.SERVER_ERROR);
        } catch (IOException | RuntimeException gone) {
            LOG.debug("Connexion déjà fermée", gone);
        }
    }

    void closeWith(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException gone) {
            LOG.debug("Connexion déjà fermée", gone);
        }
    }
}
