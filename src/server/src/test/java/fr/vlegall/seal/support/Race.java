package fr.vlegall.seal.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Courses des tests d'intégration (A06) : chaque concurrent attend le même signal de départ, puis tous partent. */
public final class Race {

    private static final long TIMEOUT_SECONDS = 60;

    /** Un concurrent, qui reçoit son rang, de 0 à n − 1. */
    @FunctionalInterface
    public interface Contender<T> {
        T run(int index) throws Exception;
    }

    private Race() {}

    /** Résultats des {@code contenders} concurrents, dans l'ordre de leur rang. */
    public static <T> List<T> run(int contenders, Contender<T> contender) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return contender.run(index);
                }));
            }
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrents non prêts");
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
