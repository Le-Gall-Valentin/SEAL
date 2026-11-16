package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IssuedChallenge;

/** KEY-03 : défi de preuve de possession ; un nouveau défi remplace le précédent (ADR 0025, point 8). */
public interface CreateChallengeUseCase {

    IssuedChallenge create(UserId user);
}
