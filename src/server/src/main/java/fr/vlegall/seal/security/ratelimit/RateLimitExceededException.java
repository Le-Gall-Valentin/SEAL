package fr.vlegall.seal.security.ratelimit;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** SEC-10 : limite atteinte ; la réponse est un 429 {@code RATE_LIMITED} avec {@code Retry-After}. */
public final class RateLimitExceededException extends SealException {

    private static final long serialVersionUID = 1L;

    private final RateLimit limit;
    private final Duration retryAfter;

    public RateLimitExceededException(RateLimit limit, Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED);
        this.limit = Objects.requireNonNull(limit, "limit");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public RateLimit limit() {
        return limit;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.of(retryAfter);
    }
}
