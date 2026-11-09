package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.logging.LogValues;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SEC-10 : seaux à jetons Bucket4j en mémoire, un par limite et par clé (ADR 0013).
 *
 * <ul>
 *   <li>Fenêtre fixe : un seau se remplit entièrement à la fin de sa période, comptée depuis sa création ; autour d'une
 *       frontière, jusqu'à deux fois la capacité peut passer en peu de temps.
 *   <li>Mémoire bornée : un seau est indexé par l'empreinte SHA-256 de sa clé, de taille fixe quelle que soit la
 *       saisie ; leur nombre est plafonné, et au-delà toute nouvelle clé est refusée (échec fermé). Les seaux redevenus
 *       pleins sont purgés chaque minute.
 *   <li>Journal : le premier refus d'un seau dans une période écrit un WARN, puis un événement
 *       {@code RATE_LIMIT_EXCEEDED} dont la clé est filtrée selon sa nature ; les refus suivants ne coûtent qu'un 429.
 * </ul>
 *
 * <p>Contrat d'appel : hors de toute transaction, depuis la couche web, la limite par IP avant toute limite dont la
 * clé est saisie. Pour ne compter que des échecs (AUTH-22), on réserve un jeton avant l'essai ({@link #consume}) et
 * on le rend en cas de succès ({@link #refund}).
 */
@Component
public final class RateLimiter {

    static final int DEFAULT_MAX_BUCKETS = 100_000;
    static final Duration CAPACITY_RETRY_AFTER = Duration.ofMinutes(1);

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

    private final ClockTimeMeter timeMeter;
    private final SecurityJournal journal;
    private final int maxBuckets;
    private final ConcurrentMap<String, Tracked> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean capacityAlerted = new AtomicBoolean();

    @Autowired
    public RateLimiter(Clock clock, SecurityJournal journal) {
        this(clock, journal, DEFAULT_MAX_BUCKETS);
    }

    RateLimiter(Clock clock, SecurityJournal journal, int maxBuckets) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.journal = Objects.requireNonNull(journal, "journal");
        this.maxBuckets = maxBuckets;
    }

    /** Prend un jeton d'une limite par IP, identifiant saisi ou courriel saisi, ou refuse. */
    public void consume(RateLimit limit, String key) {
        requireKind(limit, false);
        consume(limit, key, null);
    }

    /** Prend un jeton d'une limite par utilisateur, ou refuse ; un refus est rattaché à cet utilisateur. */
    public void consume(RateLimit limit, UserId user) {
        requireKind(limit, true);
        consume(limit, user.value().toString(), user);
    }

    /** Rend un jeton réservé par {@link #consume(RateLimit, String)}, sans dépasser la capacité ; ne crée aucun seau. */
    public void refund(RateLimit limit, String key) {
        requireKind(limit, false);
        refundToken(limit, key);
    }

    /** Rend un jeton réservé par {@link #consume(RateLimit, UserId)}. */
    public void refund(RateLimit limit, UserId user) {
        requireKind(limit, true);
        refundToken(limit, user.value().toString());
    }

    /**
     * Jetons restants d'une limite par utilisateur, sans en prendre ni créer de seau : « 2 renvois restants cette
     * heure » de Verification-Courriel (AUTH-14, ADR 0019). Simple lecture, permise dans une transaction.
     */
    public long remaining(RateLimit limit, UserId user) {
        requireKind(limit, true);
        Tracked tracked = buckets.get(id(limit, user.value().toString()));
        return tracked == null ? limit.capacity() : tracked.bucket().getAvailableTokens();
    }

    @Scheduled(fixedDelay = 60_000)
    public void purgeRefilledBuckets() {
        for (String id : buckets.keySet()) {
            buckets.computeIfPresent(id, (key, tracked) -> isFull(tracked) ? null : tracked);
        }
        capacityAlerted.set(false);
    }

    int bucketCount() {
        return buckets.size();
    }

    Set<String> bucketIds() {
        return Set.copyOf(buckets.keySet());
    }

    private void consume(RateLimit limit, String key, @Nullable UserId user) {
        requireNoTransaction();
        ConsumptionProbe[] probe = new ConsumptionProbe[1];
        Tracked tracked = buckets.compute(id(limit, key), (id, existing) -> {
            Tracked current = existing;
            if (current == null) {
                if (buckets.size() >= maxBuckets) {
                    return null;
                }
                current = track(limit);
            }
            probe[0] = current.bucket().tryConsumeAndReturnRemaining(1);
            return current;
        });
        if (tracked == null) {
            refuseForCapacity(limit);
        } else if (!probe[0].isConsumed()) {
            refuse(limit, key, user, tracked, probe[0].getNanosToWaitForRefill());
        }
    }

    private void refundToken(RateLimit limit, String key) {
        requireNoTransaction();
        buckets.computeIfPresent(id(limit, key), (id, tracked) -> {
            tracked.bucket().addTokens(1);
            return tracked;
        });
    }

    private Tracked track(RateLimit limit) {
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.period())
                        .build())
                .withCustomTimePrecision(timeMeter)
                .build();
        return new Tracked(limit, bucket, new AtomicLong(Long.MIN_VALUE));
    }

    private void refuse(RateLimit limit, String key, @Nullable UserId user, Tracked tracked, long nanosToWait) {
        if (tracked.claimJournal(timeMeter.currentTimeNanos(), nanosToWait)) {
            Map<String, String> details = new HashMap<>();
            details.put("limit", limit.name());
            String recordedKey = recordedKey(limit.kind(), key);
            if (recordedKey != null) {
                details.put("key", recordedKey);
            }
            alert(new SecurityEvent(SecurityEventType.RATE_LIMIT_EXCEEDED, user, details));
        }
        throw new RateLimitExceededException(limit, Duration.ofNanos(nanosToWait));
    }

    private void refuseForCapacity(RateLimit limit) {
        if (capacityAlerted.compareAndSet(false, true)) {
            alert(new SecurityEvent(
                    SecurityEventType.RATE_LIMIT_EXCEEDED, null, Map.of("limit", limit.name(), "reason", "capacity")));
        }
        throw new RateLimitExceededException(limit, CAPACITY_RETRY_AFTER);
    }

    /** A09 : l'alerte d'abord, pour qu'elle survive à un journal indisponible ; la limite tient dans tous les cas. */
    private void alert(SecurityEvent event) {
        LOG.warn("Limite de débit dépassée, détail dans security_events");
        try {
            journal.recordRefusal(event);
        } catch (RuntimeException e) {
            LOG.error("Journal de sécurité indisponible : dépassement non enregistré", e);
        }
    }

    private static @Nullable String recordedKey(RateLimitKind kind, String key) {
        return switch (kind) {
            case IP -> LogValues.sanitize(key);
            case ENTERED -> LogValues.identifier(key);
            case EMAIL -> LogValues.email(key);
            case USER -> null;
        };
    }

    private static boolean isFull(Tracked tracked) {
        return tracked.bucket().getAvailableTokens() >= tracked.limit().capacity();
    }

    private static void requireKind(RateLimit limit, boolean user) {
        if ((limit.kind() == RateLimitKind.USER) != user) {
            throw new IllegalArgumentException("Clé incompatible avec la limite " + limit + " (" + limit.kind() + ")");
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RateLimiter s'appelle hors transaction, depuis la couche web");
        }
    }

    private static String id(RateLimit limit, String key) {
        byte[] digest = Digests.sha256(key.getBytes(StandardCharsets.UTF_8));
        return limit.name() + ':' + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * Un seau et l'instant, en nanosecondes de l'horloge du limiteur, avant lequel un nouveau refus n'est plus
     * journalisé.
     */
    private record Tracked(RateLimit limit, Bucket bucket, AtomicLong journalQuietUntil) {

        boolean claimJournal(long now, long nanosToWait) {
            long quietUntil = journalQuietUntil.get();
            return now >= quietUntil && journalQuietUntil.compareAndSet(quietUntil, now + nanosToWait);
        }
    }
}
