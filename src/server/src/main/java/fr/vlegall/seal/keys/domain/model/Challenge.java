package fr.vlegall.seal.keys.domain.model;

import java.time.Duration;

/** Défi de preuve de possession (KEY-03) : 32 octets, 5 minutes, un seul usage, gardé haché en SHA-256. */
public final class Challenge {

    /** KEY-03 : 32 octets. */
    public static final int BYTES = ProofOfPossession.CHALLENGE_BYTES;

    /** KEY-03 : 5 minutes. */
    public static final Duration VALIDITY = Duration.ofMinutes(5);

    private Challenge() {}
}
