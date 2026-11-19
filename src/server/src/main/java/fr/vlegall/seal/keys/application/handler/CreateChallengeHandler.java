package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.keys.application.port.in.CreateChallengeUseCase;
import fr.vlegall.seal.keys.domain.model.Challenge;
import fr.vlegall.seal.keys.domain.model.IssuedChallenge;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import fr.vlegall.seal.keys.domain.port.out.RandomBytes;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CreateChallengeHandler implements CreateChallengeUseCase {

    private final ChallengeRepository challenges;
    private final RandomBytes random;
    private final Clock clock;

    public CreateChallengeHandler(ChallengeRepository challenges, RandomBytes random, Clock clock) {
        this.challenges = challenges;
        this.random = random;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedChallenge create(UserId user) {
        byte[] value = random.next(Challenge.BYTES);
        // Servie à la milliseconde, comme les autres dates de l'API (ISO 8601).
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.MILLIS).plus(Challenge.VALIDITY);
        challenges.renew(user, Digests.sha256(value), expiresAt);
        return new IssuedChallenge(value, expiresAt);
    }
}
