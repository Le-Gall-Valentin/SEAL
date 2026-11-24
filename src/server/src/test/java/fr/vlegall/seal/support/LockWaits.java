package fr.vlegall.seal.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Courses orchestrées des tests d'intégration (A06) : une transaction tient un verrou pendant qu'une autre l'attend, pour
 * vérifier ce que la seconde voit une fois libérée, sans dépendre de l'ordonnancement.
 */
public final class LockWaits {

    private static final long TIMEOUT_SECONDS = 10;

    private LockWaits() {}

    /** Attend qu'une requête contenant {@code queryFragment} soit bloquée sur un verrou de PostgreSQL. */
    public static void awaitWaiter(JdbcClient jdbc, String queryFragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                + " AND position(:fragment IN query) > 0")
                        .param("fragment", queryFragment)
                        .query(Long.class)
                        .single()
                == 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("aucune requête n'attend le verrou");
            }
            Thread.sleep(10);
        }
    }

    /** Attend le signal de libération, dans une transaction qui ne peut pas lever d'exception vérifiée. */
    public static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
