package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Table {@code email_codes} (ADR 0009) : codes hachés, avec leur compteur d'essais. */
public interface EmailCodeRepository {

    /** Invalide les codes vivants du compte, puis enregistre le nouveau code haché (AUTH-14, AUTH-17). */
    void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt);

    /** Expiration du code vivant du compte à {@code now}, vide s'il n'y en a aucun (ADR 0019, point 5). */
    Optional<Instant> liveExpiry(UserId user, Instant now);

    /** Code vivant du compte à {@code now} : non consommé, non expiré, moins de 5 essais. */
    Optional<LiveCode> findLive(UserId user, Instant now);

    /** Compte un essai manqué ; au cinquième, le code n'est plus vivant (SEC-10). */
    void countFailedAttempt(UUID code);

    void consume(UUID code, Instant now);
}
