package fr.vlegall.seal.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoundedMailExecutorTest {

    @Test
    @DisplayName("AUTH-14 : les courriels partent sur un fil unique, hors de la requête (ADR 0019, point 8)")
    void auth14MailsLeaveOnASingleBackgroundThread() throws Exception {
        BoundedMailExecutor executor = new BoundedMailExecutor();
        try {
            CompletableFuture<Thread> thread = new CompletableFuture<>();
            executor.execute(() -> thread.complete(Thread.currentThread()));

            Thread sending = thread.get(5, TimeUnit.SECONDS);
            assertThat(sending.getName()).isEqualTo("seal-mail");
            assertThat(sending.isDaemon()).isTrue();
            assertThat(sending).isNotSameAs(Thread.currentThread());
        } finally {
            executor.destroy();
        }
    }

    @Test
    @DisplayName("AUTH-14 : la file d'envoi est bornée ; au-delà, l'envoi est refusé au lieu de grossir sans fin (A06)")
    void a06QueueIsBounded() throws Exception {
        assertThat(BoundedMailExecutor.QUEUE_CAPACITY).isEqualTo(1_000);
        BoundedMailExecutor executor = new BoundedMailExecutor(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                awaitQuietly(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});

            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.destroy();
        }
    }

    @Test
    @DisplayName("AUTH-14 : à l'arrêt, le courriel en cours et ceux de la file partent avant la fermeture")
    void auth14ShutdownSendsQueuedMails() throws Exception {
        BoundedMailExecutor executor = new BoundedMailExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(2);
        executor.execute(() -> {
            started.countDown();
            awaitQuietly(release);
            sent.countDown();
        });
        executor.execute(sent::countDown);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        Thread stopping = new Thread(() -> {
            try {
                executor.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        stopping.start();
        // L'arrêt a commencé dès que la file refuse un nouvel envoi ; le premier courriel est encore en cours.
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> refuses(executor));
        release.countDown();
        stopping.join(10_000);

        assertThat(sent.getCount()).isZero();
    }

    private static boolean refuses(BoundedMailExecutor executor) {
        try {
            executor.execute(() -> {});
            return false;
        } catch (RejectedExecutionException e) {
            return true;
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
