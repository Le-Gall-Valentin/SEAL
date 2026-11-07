package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.logging.LogValues;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SEC-10 : seaux à jetons Bucket4j en mémoire, un par limite et par clé (IP, identifiant saisi, utilisateur). Chaque
 * seau se remplit entièrement à la fin de sa période. Un refus lève {@link RateLimitExceededException}, est journalisé
 * dans {@code security_events} et signalé en WARN (A09).
 *
 * <p>Les clés venant parfois de saisies arbitraires, les seaux redevenus pleins sont purgés chaque minute : un seau
 * plein ne porte plus aucune information.
 *
 * <p>Contrat d'appel : depuis la couche web, hors de toute transaction. Un refus écrit dans le journal par une
 * transaction indépendante ; appelé dans une transaction, chaque refus retiendrait deux connexions, et une rafale de
 * refus suffirait à épuiser le pool. Pour ne compter que des échecs (AUTH-22), on réserve un jeton avant l'essai
 * ({@link #consume}) et on le rend en cas de succès ({@link #refund}) : des essais parallèles ne peuvent pas passer
 * entre une vérification et un décompte.
 */
@Component
public class RateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

    private final ClockTimeMeter timeMeter;
    private final SecurityJournal journal;
    private final ConcurrentMap<String, Tracked> buckets = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock, SecurityJournal journal) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.journal = journal;
    }

    /** Prend un jeton, ou refuse si la limite est atteinte. */
    public void consume(RateLimit limit, String key) {
        requireNoTransaction();
        ConsumptionProbe probe = buckets.computeIfAbsent(id(limit, key), id -> track(limit))
                .bucket()
                .tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            refuse(limit, key, probe.getNanosToWaitForRefill());
        }
    }

    /** Rend un jeton réservé par {@link #consume}, sans jamais dépasser la capacité ; ne crée aucun seau. */
    public void refund(RateLimit limit, String key) {
        requireNoTransaction();
        Tracked tracked = buckets.get(id(limit, key));
        if (tracked != null) {
            tracked.bucket().addTokens(1);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void purgeRefilledBuckets() {
        buckets.values()
                .removeIf(tracked ->
                        tracked.bucket().getAvailableTokens() >= tracked.limit().capacity());
    }

    int bucketCount() {
        return buckets.size();
    }

    private Tracked track(RateLimit limit) {
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.period())
                        .build())
                .withCustomTimePrecision(timeMeter)
                .build();
        return new Tracked(limit, bucket);
    }

    private void refuse(RateLimit limit, String key, long nanosToWait) {
        String safeKey = LogValues.identifier(key);
        journal.record(new SecurityEvent(
                SecurityEventType.RATE_LIMIT_EXCEEDED, null, Map.of("limit", limit.name(), "key", safeKey)));
        // A09 : alerte sans aucune donnée variable ; la limite, la clé masquée et l'IP sont dans security_events
        // (SEC-21), à la même date que cette ligne.
        LOG.warn("Limite de débit dépassée, détail dans security_events");
        throw new RateLimitExceededException(limit, Duration.ofNanos(nanosToWait));
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RateLimiter s'appelle hors transaction, depuis la couche web");
        }
    }

    private static String id(RateLimit limit, String key) {
        return limit.name() + ':' + key;
    }

    private record Tracked(RateLimit limit, Bucket bucket) {}
}
