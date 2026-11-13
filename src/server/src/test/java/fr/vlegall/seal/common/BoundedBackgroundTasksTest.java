package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BoundedBackgroundTasksTest {

    private final BoundedBackgroundTasks tasks = new BoundedBackgroundTasks(1);

    @AfterEach
    void stop() throws InterruptedException {
        MDC.clear();
        tasks.destroy();
    }

    @Test
    @DisplayName("AUTH-70 : une tâche tourne hors du fil de la requête, avec son identifiant de corrélation (SEC-21)")
    void auth70TaskRunsOffTheRequestThreadWithItsCorrelationId() throws InterruptedException {
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        MDC.put("correlationId", "corr-42");

        tasks.submit(() -> {
            seen.add(Thread.currentThread().getName());
            seen.add(MDC.get("correlationId"));
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).containsExactly("seal-background", "corr-42");
    }

    @Test
    @DisplayName("A10 : une tâche en échec est journalisée sans arrêter les suivantes")
    void a10FailingTaskDoesNotStopTheNextOnes(CapturedOutput output) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        tasks.submit(() -> {
            throw new IllegalStateException("base indisponible");
        });
        tasks.submit(done::countDown);

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output).contains("Tâche d'arrière-plan en échec : IllegalStateException");
    }

    @Test
    @DisplayName("A06 : une file pleine abandonne la tâche sans lever d'exception")
    void a06FullQueueDropsTheTask(CapturedOutput output) throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        tasks.submit(() -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        tasks.submit(() -> {});

        assertThatNoException().isThrownBy(() -> tasks.submit(() -> {}));
        release.countDown();
        assertThat(output).contains("File des tâches d'arrière-plan pleine");
    }

    @Test
    @DisplayName("AUTH-14 : à l'arrêt, la tâche en cours et celles de la file finissent avant la fermeture")
    void auth14ShutdownRunsTheQueuedTasks() throws Exception {
        BoundedBackgroundTasks stopping = new BoundedBackgroundTasks(10);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        stopping.submit(() -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        });
        stopping.submit(done::countDown);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        Thread stopper = new Thread(() -> {
            try {
                stopping.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        stopper.start();
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(stopping::isStopping);
        release.countDown();
        stopper.join(10_000);

        assertThat(done.getCount()).isZero();
    }
}
