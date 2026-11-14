package fr.vlegall.seal.keys.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Défi de preuve de possession (KEY-03) : 32 octets, 5 minutes, un seul usage, gardé haché en SHA-256. */
public final class Challenge {

    /** KEY-03 : 32 octets. */
    public static final int BYTES = ProofOfPossession.CHALLENGE_BYTES;

    /** KEY-03 : 5 minutes. */
    public static final Duration VALIDITY = Duration.ofMinutes(5);

    private Challenge() {}

    /** Défi tel que {@code key_challenges} le garde : son haché et son expiration. */
    public record Stored(byte[] hash, Instant expiresAt) {

        public Stored {
            hash = Objects.requireNonNull(hash, "hash").clone();
            Objects.requireNonNull(expiresAt, "expiresAt");
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stored that && Arrays.equals(hash, that.hash) && expiresAt.equals(that.expiresAt);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return "Stored[hash=***, expiresAt=" + expiresAt + "]";
        }
    }
}
