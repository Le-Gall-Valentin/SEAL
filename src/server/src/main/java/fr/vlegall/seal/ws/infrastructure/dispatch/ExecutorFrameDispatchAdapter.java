package fr.vlegall.seal.ws.infrastructure.dispatch;

import fr.vlegall.seal.ws.domain.port.out.FrameDispatcher;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * TRN-13, ADR 0044 (décision 10) : envois et fermetures des WebSockets sur un petit groupe de fils et une file bornée
 * (A06). Un envoi bloqué ne tient qu'un fil, 10 secondes au plus (délai d'envoi de Tomcat) ; une file pleine abandonne
 * la tâche avec un WARN. Une tâche en échec est journalisée par le seul nom de son exception.
 */
@Component
public final class ExecutorFrameDispatchAdapter implements FrameDispatcher, DisposableBean {

    static final int THREADS = 4;
    static final int QUEUE_CAPACITY = 10_000;

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorFrameDispatchAdapter.class);

    private final ThreadPoolExecutor executor;

    public ExecutorFrameDispatchAdapter() {
        this(THREADS, QUEUE_CAPACITY);
    }

    ExecutorFrameDispatchAdapter(int threads, int capacity) {
        AtomicInteger count = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "seal-ws-send-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void dispatch(Runnable task) {
        try {
            executor.execute(() -> run(task));
        } catch (RejectedExecutionException full) {
            LOG.warn(
                    executor.isShutdown()
                            ? "Arrêt en cours : envoi abandonné"
                            : "File des envois pleine : envoi abandonné");
        }
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failed) {
            LOG.warn("Envoi WebSocket en échec : {}", failed.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
