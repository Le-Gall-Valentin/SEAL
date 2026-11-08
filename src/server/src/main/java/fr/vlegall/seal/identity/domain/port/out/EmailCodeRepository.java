package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Optional;

/** Table {@code email_codes} (ADR 0009) : codes hachés, avec leur compteur d'essais. */
public interface EmailCodeRepository {

    /** Invalide les codes vivants du compte, puis enregistre le nouveau code haché (AUTH-14, AUTH-17). */
    void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt);

    /** Expiration du dernier code non consommé du compte. */
    Optional<Instant> latestExpiry(UserId user);
}
