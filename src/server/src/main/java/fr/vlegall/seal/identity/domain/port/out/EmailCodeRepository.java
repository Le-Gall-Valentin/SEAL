package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.model.LiveEmailChange;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Table {@code email_codes} (ADR 0009) : codes hachés, avec leur compteur d'essais. Un code a un usage : vérifier le
 * courriel d'un compte en attente (AUTH-14, AUTH-17) ou confirmer le changement de courriel d'un compte actif (ACC-02,
 * avec la nouvelle adresse). Un code ne sert jamais à l'autre usage.
 */
public interface EmailCodeRepository {

    /** Invalide le code de vérification vivant du compte, puis enregistre le nouveau, haché (AUTH-14, AUTH-17). */
    void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt);

    /** Expiration du code vivant du compte à {@code now}, vide s'il n'y en a aucun (ADR 0019, point 5). */
    Optional<Instant> liveExpiry(UserId user, Instant now);

    /** Code de vérification vivant du compte à {@code now} : non consommé, non expiré, moins de 5 essais. */
    Optional<LiveCode> findLive(UserId user, Instant now);

    /**
     * ACC-02 : invalide le code de changement vivant du compte, puis enregistre le nouveau, haché, avec l'adresse qu'il
     * confirme. Les codes de vérification ne sont pas touchés.
     */
    void renewChange(UserId user, EmailAddress target, byte[] codeHash, Instant issuedAt, Instant expiresAt);

    /** ACC-02 : code de changement vivant du compte à {@code now} : non consommé, non expiré, moins de 5 essais. */
    Optional<LiveEmailChange> findLiveChange(UserId user, Instant now);

    /** Compte un essai manqué ; au cinquième, le code n'est plus vivant (SEC-10). */
    void countFailedAttempt(UUID code);

    void consume(UUID code, Instant now);
}
