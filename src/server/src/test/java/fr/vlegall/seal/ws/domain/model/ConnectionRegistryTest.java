package fr.vlegall.seal.ws.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectionRegistryTest {

    private static final UserId ALICE = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000001"));
    private static final UserId BOB = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000002"));

    private final ConnectionRegistry registry = new ConnectionRegistry();

    private static Connection connection(UserId user, String session) {
        return new Connection() {
            private final String id = UUID.randomUUID().toString();

            @Override
            public String id() {
                return id;
            }

            @Override
            public UserId user() {
                return user;
            }

            @Override
            public String sessionId() {
                return session;
            }

            @Override
            public void send(String text) {}

            @Override
            public void close(CloseReason reason) {}
        };
    }

    @Test
    @DisplayName("TRN-13 : cinq connexions par utilisateur ; la sixième est refusée, une place libérée se reprend")
    void trn13FiveConnectionsPerUser() {
        List<Connection> opened = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Connection connection = connection(ALICE, "s" + i);
            assertThat(registry.open(connection)).isTrue();
            opened.add(connection);
        }

        assertThat(registry.open(connection(ALICE, "s5"))).isFalse();
        assertThat(registry.open(connection(BOB, "b"))).as("un autre compte").isTrue();
        registry.close(opened.getFirst());
        assertThat(registry.open(connection(ALICE, "s6"))).isTrue();
        assertThat(registry.byUser(ALICE)).hasSize(5);
    }

    @Test
    @DisplayName("TRN-12 : les connexions se retrouvent par session HTTP et par compte")
    void trn12FindsConnectionsBySessionAndUser() {
        Connection first = connection(ALICE, "session-a");
        Connection second = connection(ALICE, "session-a");
        Connection other = connection(ALICE, "session-b");
        Connection bob = connection(BOB, "session-c");
        List.of(first, second, other, bob).forEach(registry::open);

        assertThat(registry.bySession("session-a")).containsExactlyInAnyOrder(first, second);
        assertThat(registry.byUser(ALICE)).containsExactlyInAnyOrder(first, second, other);
        assertThat(registry.all()).hasSize(4);
        registry.close(first);
        registry.close(first);
        assertThat(registry.bySession("session-a")).containsExactly(second);
        assertThat(registry.bySession("inconnue")).isEmpty();
    }

    @Test
    @DisplayName("TRN-13 : vingt ouvertures simultanées d'un même compte n'en acceptent que cinq")
    void trn13ConcurrentOpensNeverExceedFive() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                Connection connection = connection(ALICE, "s" + i);
                results.add(pool.submit(() -> {
                    start.await();
                    return registry.open(connection);
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(5);
        }
        assertThat(registry.byUser(ALICE)).hasSize(5);
    }
}
