package fr.vlegall.seal.ws.infrastructure.web;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * TRN-12, ADR 0044 (décision 10) : ferme les connexions hors du fil appelant, car Tomcat écrit la trame de fermeture de
 * façon bloquante (50 ms au plus pour les codes de SEAL, jamais 1000). Une fermeture n'est jamais abandonnée : file
 * pleine ou arrêt en cours, elle se fait sur le fil appelant.
 */
@Component
final class ConnectionCloser implements Closer, DisposableBean {

    static final int THREADS = 2;
    static final int QUEUE_CAPACITY = 10_000;

    private final ThreadPoolExecutor executor;

    ConnectionCloser() {
        this(THREADS, QUEUE_CAPACITY);
    }

    ConnectionCloser(int threads, int capacity) {
        AtomicInteger count = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "seal-ws-close-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void close(Runnable close) {
        try {
            executor.execute(close);
        } catch (RejectedExecutionException full) {
            close.run();
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
