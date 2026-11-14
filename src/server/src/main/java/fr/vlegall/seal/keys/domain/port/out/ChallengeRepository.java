package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;

/** Table {@code key_challenges} : un défi vivant par compte, gardé haché (ADR 0025, point 8). */
public interface ChallengeRepository {

    /** Remplace le défi du compte, quel qu'il soit, par celui-ci. */
    void renew(UserId user, byte[] hash, Instant expiresAt);

    /** Consomme le défi s'il est celui du compte, vivant et jamais utilisé ; faux sinon, même sous concurrence. */
    boolean consume(UserId user, byte[] hash, Instant at);
}
