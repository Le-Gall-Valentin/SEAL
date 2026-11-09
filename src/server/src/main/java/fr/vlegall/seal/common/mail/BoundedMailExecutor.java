package fr.vlegall.seal.common.mail;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Envoie les courriels sur un fil unique, hors de la requête : un serveur SMTP lent ne la retient pas (ADR 0019,
 * point 8). La file est bornée (A06) ; à l'arrêt, les courriels déjà programmés partent pendant 5 secondes au plus.
 */
@Component
public final class BoundedMailExecutor implements MailExecutor, DisposableBean {

    static final int QUEUE_CAPACITY = 1_000;

    private final ThreadPoolExecutor executor;

    public BoundedMailExecutor() {
        this(QUEUE_CAPACITY);
    }

    BoundedMailExecutor(int capacity) {
        this.executor =
                new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "seal-mail");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void execute(Runnable sending) {
        executor.execute(sending);
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
