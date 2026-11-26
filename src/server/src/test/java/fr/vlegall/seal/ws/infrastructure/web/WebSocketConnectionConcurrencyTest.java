package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.json.JsonMapper;

class WebSocketConnectionConcurrencyTest {

    /** Rappels comme Tomcat : sur un autre fil, ou parfois aussitôt sur le fil appelant. */
    static final class AsyncSocket implements FrameSocket {
        final ExecutorService callbacks = Executors.newFixedThreadPool(4);
        final Set<String> written = ConcurrentHashMap.newKeySet();
        final AtomicInteger duplicates = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public void sendText(String text, Consumer<Boolean> done) {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            if (!written.add(text)) {
                duplicates.incrementAndGet();
            }
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                inFlight.decrementAndGet();
                done.accept(true);
            } else {
                callbacks.execute(() -> {
                    inFlight.decrementAndGet();
                    done.accept(true);
                });
            }
        }

        @Override
        public void close(CloseStatus status) {
            closes.incrementAndGet();
        }
    }

    @RepeatedTest(30)
    @DisplayName(
            "TRN-13, ADR 0044 : sous huit fils, chaque trame part une fois, une écriture à la fois, puis une seule fermeture")
    void trn13ConcurrentSendersThenClose() throws Exception {
        AsyncSocket socket = new AsyncSocket();
        WebSocketConnection connection = new WebSocketConnection(
                socket,
                new UserId(UUID.randomUUID()),
                "s",
                () -> {},
                new WireFrames(JsonMapper.builder().build()),
                Instant.EPOCH,
                Runnable::run);
        int producers = 8;
        int perProducer = 500;
        ExecutorService pool = Executors.newFixedThreadPool(producers);
        CountDownLatch start = new CountDownLatch(1);
        for (int p = 0; p < producers; p++) {
            int id = p;
            pool.execute(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < perProducer; i++) {
                    connection.send("f" + id + "-" + i);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (socket.written.size() < producers * perProducer && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(socket.written).hasSize(producers * perProducer);
        assertThat(socket.duplicates.get()).isZero();
        assertThat(socket.maxInFlight.get()).isEqualTo(1);
        // TRN-12 : deux fermetures concurrentes (requête de déconnexion et ronde) n'envoient qu'un session.revoked.
        Thread racer = new Thread(() -> connection.close(CloseReason.SESSION_ENDED));
        racer.start();
        connection.close(CloseReason.SESSION_ENDED);
        racer.join();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (socket.closes.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        Thread.sleep(50);
        assertThat(socket.closes.get()).isEqualTo(1);
        assertThat(socket.written.stream().filter(t -> t.contains("session.revoked")))
                .hasSize(1);
        socket.callbacks.shutdownNow();
    }
}
