package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Table key_challenges en mémoire : un défi vivant par compte, gardé haché (KEY-03, ADR 0025 point 8). */
final class InMemoryChallenges implements ChallengeRepository {

    /** Défi tel que {@code key_challenges} le garde : son haché et son expiration. */
    record Stored(byte[] hash, Instant expiresAt) {}

    final Map<UserId, Stored> live = new HashMap<>();

    @Override
    public void renew(UserId user, byte[] hash, Instant expiresAt) {
        live.put(user, new Stored(hash.clone(), expiresAt));
    }

    @Override
    public boolean consume(UserId user, byte[] hash, Instant at) {
        Stored stored = live.get(user);
        if (stored == null || !Arrays.equals(stored.hash(), hash) || !at.isBefore(stored.expiresAt())) {
            return false;
        }
        live.remove(user);
        return true;
    }
}
