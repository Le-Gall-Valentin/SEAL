package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.domain.model.IssuedChallenge;
import java.time.Instant;

/** Réponse de {@code POST /api/v1/keys/challenge} (KEY-03). */
public record ChallengeView(String challenge, Instant expiresAt) {

    public static ChallengeView of(IssuedChallenge issued) {
        return new ChallengeView(Base64Url.encode(issued.value()), issued.expiresAt());
    }

    /** SEC-21 : le défi n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "ChallengeView[challenge=***, expiresAt=" + expiresAt + "]";
    }
}
