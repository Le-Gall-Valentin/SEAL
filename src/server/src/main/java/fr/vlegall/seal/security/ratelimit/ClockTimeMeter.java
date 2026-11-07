package fr.vlegall.seal.security.ratelimit;

import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Fournit à Bucket4j l'heure de l'horloge injectée, pour que les tests puissent l'avancer. */
final class ClockTimeMeter implements TimeMeter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Clock clock;

    ClockTimeMeter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public long currentTimeNanos() {
        Instant now = clock.instant();
        return now.getEpochSecond() * NANOS_PER_SECOND + now.getNano();
    }

    @Override
    public boolean isWallClockBased() {
        return true;
    }
}
