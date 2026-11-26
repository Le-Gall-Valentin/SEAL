package fr.vlegall.seal.ws.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TRN-13 : connexions ouvertes, 5 au plus par utilisateur. L'ouverture et la fermeture sont atomiques par compte : deux
 * ouvertures simultanées ne dépassent jamais la limite. Les listes gardées sont immuables, lues sans verrou.
 */
public final class ConnectionRegistry {

    /** TRN-13, 15.2 : 5 connexions par utilisateur. */
    public static final int MAX_CONNECTIONS_PER_USER = 5;

    private final ConcurrentMap<UserId, List<Connection>> connections = new ConcurrentHashMap<>();

    /** Inscrit la connexion, ou refuse si son compte en a déjà 5. */
    public boolean open(Connection connection) {
        boolean[] accepted = {false};
        connections.compute(connection.user(), (user, current) -> {
            List<Connection> existing = current == null ? List.of() : current;
            if (existing.size() >= MAX_CONNECTIONS_PER_USER) {
                return current;
            }
            List<Connection> next = new ArrayList<>(existing);
            next.add(connection);
            accepted[0] = true;
            return List.copyOf(next);
        });
        return accepted[0];
    }

    /** Retire la connexion ; sans effet si elle n'est pas inscrite. */
    public void close(Connection connection) {
        connections.computeIfPresent(connection.user(), (user, current) -> {
            List<Connection> next =
                    current.stream().filter(open -> open != connection).toList();
            return next.isEmpty() ? null : next;
        });
    }

    /** Les connexions liées à cette session HTTP (TRN-12). */
    public List<Connection> bySession(String sessionId) {
        return all().stream()
                .filter(connection -> connection.sessionId().equals(sessionId))
                .toList();
    }

    /** Les connexions de ce compte. */
    public List<Connection> byUser(UserId user) {
        return connections.getOrDefault(user, List.of());
    }

    /** Toutes les connexions ouvertes. */
    public List<Connection> all() {
        return connections.values().stream().flatMap(List::stream).toList();
    }
}
