package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectionCloserTest {

    @Test
    @DisplayName("TRN-12, ADR 0044 : file pleine, la fermeture n'est pas abandonnée ; elle se fait sur le fil appelant")
    void trn12FullQueueClosesInline() throws Exception {
        ConnectionCloser closer = new ConnectionCloser(1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        try {
            closer.close(() -> {
                running.countDown();
                awaitQuietly(release);
            });
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            closer.close(() -> {});
            AtomicReference<Thread> ranOn = new AtomicReference<>();

            closer.close(() -> ranOn.set(Thread.currentThread()));

            assertThat(ranOn.get()).isSameAs(Thread.currentThread());
        } finally {
            release.countDown();
            closer.destroy();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
