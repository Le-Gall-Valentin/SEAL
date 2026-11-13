package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Table {@code password_reset_tokens} (AUTH-70) : seul le SHA-256 du jeton y figure. Un lien invalidé ou utilisé porte
 * {@code consumed_at}. L'instant {@code now} vient de l'horloge injectée, jamais de la base.
 */
public interface PasswordResetTokenRepository {

    /** Invalide les liens encore valables du compte, puis enregistre le nouveau (ADR 0022, point 4). */
    void issue(UserId user, byte[] tokenHash, Instant expiresAt, Instant now);

    /** Lien valable dont le jeton a ce haché, ou vide. */
    Optional<LiveReset> findLive(byte[] tokenHash, Instant now);

    /** Consomme le lien s'il est encore valable, de façon atomique : vrai pour un seul appelant. */
    boolean consume(UUID id, Instant now);

    /** Invalide tous les liens encore valables du compte (ADR 0022, point 4). */
    void consumeAll(UserId user, Instant now);
}
