package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.LocalRateLimit;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import fr.vlegall.seal.ws.domain.model.Connection;
import fr.vlegall.seal.ws.domain.model.SessionBinding;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Une connexion de {@code /ws}, sûre entre fils d'exécution (TRN-13, ADR 0044, décision 10). Chaque trame entre dans la
 * file d'envoi de la connexion et en sort par une écriture asynchrone, une à la fois : aucun appelant n'attend le
 * client. Plus de 512 Kio en attente, ou une écriture refusée ou trop lente (10 s), ferment la connexion en 1011. Une
 * fois la fermeture demandée, plus rien n'entre dans la file.
 */
final class WebSocketConnection implements Connection {

    /** TRN-13, ADR 0044 : octets UTF-8 au plus en attente d'écriture. */
    static final long BUFFER_SIZE_LIMIT = 512 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketConnection.class);
    /** Vrai sur le fil qui vide la file : une écriture qui finit aussitôt n'y rappelle pas {@link #drain()}. */
    private static final ThreadLocal<Boolean> DRAINING = ThreadLocal.withInitial(() -> false);

    private record Pending(String text, long bytes) {}

    private final FrameSocket socket;
    private final String id;
    private final UserId user;
    private final LocalRateLimit frameLimit;
    private final WireFrames frames;
    private final Closer closer;
    private final AtomicReference<SessionBinding> binding;
    private final Queue<Pending> outbox = new ConcurrentLinkedQueue<>();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean writing = new AtomicBoolean();
    private final AtomicReference<@Nullable CloseStatus> closing = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Instant lastHeardAt;

    WebSocketConnection(
            FrameSocket socket,
            String id,
            UserId user,
            String sessionId,
            LocalRateLimit frameLimit,
            WireFrames frames,
            Instant openedAt,
            Closer closer) {
        this.socket = socket;
        this.id = id;
        this.user = user;
        this.frameLimit = frameLimit;
        this.frames = frames;
        this.closer = closer;
        this.binding = new AtomicReference<>(SessionBinding.of(sessionId));
        this.lastHeardAt = openedAt;
    }

    WebSocketConnection(
            FrameSocket socket,
            UserId user,
            String sessionId,
            LocalRateLimit frameLimit,
            WireFrames frames,
            Instant openedAt,
            Closer closer) {
        this(socket, UUID.randomUUID().toString(), user, sessionId, frameLimit, frames, openedAt, closer);
    }

    /** La connexion d'une session ouverte par {@link FullSessionHandshakeInterceptor}. */
    static WebSocketConnection of(
            WebSocketSession session, LocalRateLimit frameLimit, WireFrames frames, Instant openedAt, Closer closer) {
        return new WebSocketConnection(
                new AsyncFrameSocket(session),
                session.getId(),
                (UserId) session.getAttributes().get(FullSessionHandshakeInterceptor.USER),
                (String) session.getAttributes().get(FullSessionHandshakeInterceptor.SESSION),
                frameLimit,
                frames,
                openedAt,
                closer);
    }

    /** TRN-13 : seau des trames de cette connexion, libéré avec elle. */
    LocalRateLimit frameLimit() {
        return frameLimit;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public UserId user() {
        return user;
    }

    @Override
    public SessionBinding binding() {
        return binding.get();
    }

    @Override
    public void follow(String renamedSessionId) {
        binding.updateAndGet(current -> current.renamedTo(renamedSessionId));
    }

    @Override
    public void settle(SessionBinding seen) {
        binding.compareAndSet(seen, seen.settled());
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
        if (closing.get() != null || closed.get()) {
            return;
        }
        long bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (queuedBytes.addAndGet(bytes) > BUFFER_SIZE_LIMIT) {
            LOG.debug("File d'envoi pleine : connexion fermée");
            finish(CloseStatus.SERVER_ERROR);
            return;
        }
        outbox.add(new Pending(text, bytes));
        drain();
    }

    @Override
    public void close(CloseReason reason) {
        if (reason == CloseReason.SESSION_ENDED && closing.get() == null && !closed.get()) {
            // TRN-12 : session.revoked, puis 4401 une fois la trame écrite ; l'identifiant d'une trame du serveur est
            // choisi par lui.
            send(frames.frame("session.revoked", UUID.randomUUID().toString(), frames.empty()));
        }
        if (closing.compareAndSet(null, new CloseStatus(reason.code()))) {
            drain();
        }
    }

    /** TRN-13 : fermeture demandée ou faite ; plus aucune trame reçue n'est traitée. */
    boolean isClosing() {
        return closing.get() != null || closed.get();
    }

    /** Fermeture immédiate, sans vider la file : trame illisible (1008) ou trop grande (1009). */
    void closeWith(CloseStatus status) {
        finish(status);
    }

    /** Écrit les trames en attente, une à la fois, puis ferme si la fermeture est demandée et la file vide. */
    private void drain() {
        boolean outer = !DRAINING.get();
        DRAINING.set(true);
        try {
            while (!closed.get() && writing.compareAndSet(false, true)) {
                Pending next = outbox.poll();
                if (next == null) {
                    writing.set(false);
                    if (!outbox.isEmpty()) {
                        continue;
                    }
                    CloseStatus status = closing.get();
                    if (status != null) {
                        finish(status);
                    }
                    return;
                }
                queuedBytes.addAndGet(-next.bytes());
                write(next.text());
            }
        } finally {
            if (outer) {
                DRAINING.remove();
            }
        }
    }

    private void write(String text) {
        try {
            socket.sendText(text, written -> {
                writing.set(false);
                if (!written) {
                    LOG.debug("Trame non écrite : connexion fermée");
                    finish(CloseStatus.SERVER_ERROR);
                } else if (!DRAINING.get()) {
                    drain();
                }
            });
        } catch (RuntimeException failed) {
            // A10 : une socket déjà fermée ou un encodage impossible ne remontent jamais à l'appelant.
            LOG.debug(
                    "Trame non écrite ({}) : connexion fermée",
                    failed.getClass().getSimpleName());
            writing.set(false);
            finish(CloseStatus.SERVER_ERROR);
        }
    }

    /** Ferme une seule fois, hors du fil appelant : la trame de fermeture s'écrit de façon bloquante. */
    private void finish(CloseStatus status) {
        if (closed.compareAndSet(false, true)) {
            outbox.clear();
            closer.close(() -> socket.close(status), () -> socket.closeQuickly(status));
        }
    }
}
