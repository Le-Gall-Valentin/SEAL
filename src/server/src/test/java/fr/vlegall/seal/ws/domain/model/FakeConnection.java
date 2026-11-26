package fr.vlegall.seal.ws.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/** Connexion en mémoire, pour tester ce qui l'entoure sans WebSocket. */
public final class FakeConnection implements Connection {

    private final String id = UUID.randomUUID().toString();
    private final UserId user;
    private final Instant lastHeardAt;
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final AtomicReference<SessionBinding> binding;
    private volatile @Nullable CloseReason closed;
    private final boolean failing;

    public FakeConnection(UserId user, String sessionId, Instant lastHeardAt) {
        this(user, sessionId, lastHeardAt, false);
    }

    private FakeConnection(UserId user, String sessionId, Instant lastHeardAt, boolean failing) {
        this.user = user;
        this.binding = new AtomicReference<>(SessionBinding.of(sessionId));
        this.lastHeardAt = lastHeardAt;
        this.failing = failing;
    }

    /** Une connexion dont chaque envoi et chaque fermeture échouent, comme un client qui ne lit plus. */
    public static FakeConnection failing(UserId user, String sessionId, Instant lastHeardAt) {
        return new FakeConnection(user, sessionId, lastHeardAt, true);
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

    @Override
    public void send(String text) {
        if (failing) {
            throw new IllegalStateException("envoi impossible");
        }
        sent.add(text);
    }

    @Override
    public void close(CloseReason reason) {
        if (failing) {
            throw new IllegalStateException("fermeture impossible");
        }
        closed = reason;
    }

    /** Les trames envoyées. */
    public List<String> sent() {
        return List.copyOf(sent);
    }

    /** La cause de fermeture, vide si la connexion est ouverte. */
    public Optional<CloseReason> closed() {
        return Optional.ofNullable(closed);
    }
}
