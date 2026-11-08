package fr.vlegall.seal.identity.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.port.out.ResendAllowance;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import org.springframework.stereotype.Component;

/** Renvois restants : le seau {@code EMAIL_CODE_RESENDS_PER_USER}, lu sans être consommé. */
@Component
public class ResendAllowanceAdapter implements ResendAllowance {

    private final RateLimiter limiter;

    public ResendAllowanceAdapter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public long remaining(UserId user) {
        return limiter.remaining(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user);
    }
}
