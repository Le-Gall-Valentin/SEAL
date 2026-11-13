package fr.vlegall.seal.common;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Tâches d'arrière-plan sur un fil unique et une file bornée (A06), comme l'envoi des courriels (ADR 0019, point 8).
 * L'identifiant de corrélation de la requête suit la tâche (SEC-21). Une tâche en échec est journalisée par le seul nom
 * de son exception, sans arrêter les suivantes. À l'arrêt, les tâches programmées ont 5 secondes pour finir.
 */
@Component
public final class BoundedBackgroundTasks implements BackgroundTasks, DisposableBean {

    static final int QUEUE_CAPACITY = 1_000;

    private static final Logger LOG = LoggerFactory.getLogger(BoundedBackgroundTasks.class);

    private final ThreadPoolExecutor executor;

    public BoundedBackgroundTasks() {
        this(QUEUE_CAPACITY);
    }

    BoundedBackgroundTasks(int capacity) {
        this.executor =
                new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "seal-background");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void submit(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        try {
            executor.execute(() -> run(task, context));
        } catch (RejectedExecutionException e) {
            LOG.warn("File des tâches d'arrière-plan pleine : tâche abandonnée");
        }
    }

    private static void run(Runnable task, @Nullable Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        try {
            task.run();
        } catch (RuntimeException e) {
            LOG.error("Tâche d'arrière-plan en échec : {}", e.getClass().getSimpleName());
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
