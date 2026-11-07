package fr.vlegall.seal.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Horloge de test que l'on avance à volonté. Figée ({@link #fixedAt}) pour les tests unitaires, qui comparent des
 * durées exactes ; calée sur l'heure système ({@link #followingSystem}) pour les tests d'intégration, où d'autres
 * composants (Spring Session) datent leurs objets avec l'heure réelle.
 */
public final class MutableClock extends Clock {

    private final @Nullable Instant base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    private MutableClock(@Nullable Instant base) {
        this.base = base;
    }

    public static MutableClock fixedAt(Instant instant) {
        return new MutableClock(instant);
    }

    public static MutableClock followingSystem() {
        return new MutableClock(null);
    }

    public void advance(Duration duration) {
        offset.updateAndGet(current -> current.plus(duration));
    }

    /** Annule les avances : à appeler après chaque test qui avance l'horloge partagée du contexte. */
    public void reset() {
        offset.set(Duration.ZERO);
    }

    @Override
    public Instant instant() {
        return (base == null ? Instant.now() : base).plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("horloge de test toujours en UTC");
    }
}
