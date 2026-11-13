package fr.vlegall.seal.security.ratelimit;

import java.util.Objects;

/** Un jeton à prendre pour une requête : une limite dont la clé est une IP ou une saisie, et cette clé. */
public record RateLimitToken(RateLimit limit, String key) {

    public RateLimitToken {
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(key, "key");
    }
}
