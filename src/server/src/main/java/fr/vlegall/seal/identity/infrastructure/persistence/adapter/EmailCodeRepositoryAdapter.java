package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code email_codes} par {@link JdbcClient} : le haché seul est stocké (DATA-04). */
@Component
public class EmailCodeRepositoryAdapter implements EmailCodeRepository {

    /** Code vivant : non consommé, non expiré, moins de 5 essais (AUTH-14, SEC-10). */
    private static final String LIVE =
            "user_id = :user AND consumed_at IS NULL AND expires_at > :now " + "AND attempts < :maxAttempts";

    private final JdbcClient jdbc;

    public EmailCodeRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt) {
        jdbc.sql("UPDATE email_codes SET consumed_at = :now WHERE user_id = :user AND consumed_at IS NULL")
                .param("now", SqlSupport.utc(issuedAt))
                .param("user", user.value())
                .update();
        jdbc.sql("INSERT INTO email_codes (user_id, purpose, code_hash, expires_at) "
                        + "VALUES (:user, 'VERIFY_EMAIL', :hash, :expiresAt)")
                .param("user", user.value())
                .param("hash", codeHash)
                .param("expiresAt", SqlSupport.utc(expiresAt))
                .update();
    }

    @Override
    public Optional<Instant> liveExpiry(UserId user, Instant now) {
        return jdbc.sql("SELECT expires_at FROM email_codes WHERE " + LIVE + " ORDER BY id DESC LIMIT 1")
                .param("user", user.value())
                .param("now", SqlSupport.utc(now))
                .param("maxAttempts", CodePolicy.MAX_ATTEMPTS)
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant);
    }

    @Override
    public Optional<LiveCode> findLive(UserId user, Instant now) {
        return jdbc.sql("SELECT id, code_hash FROM email_codes WHERE " + LIVE + " ORDER BY id DESC LIMIT 1")
                .param("user", user.value())
                .param("now", SqlSupport.utc(now))
                .param("maxAttempts", CodePolicy.MAX_ATTEMPTS)
                .query((row, number) -> new LiveCode(row.getObject("id", UUID.class), row.getBytes("code_hash")))
                .optional();
    }

    @Override
    public void countFailedAttempt(UUID code) {
        SqlSupport.requireOneRow(
                jdbc.sql("UPDATE email_codes SET attempts = attempts + 1 WHERE id = :id")
                        .param("id", code)
                        .update(),
                "essai manqué");
    }

    @Override
    public void consume(UUID code, Instant now) {
        SqlSupport.requireOneRow(
                jdbc.sql("UPDATE email_codes SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                        .param("now", SqlSupport.utc(now))
                        .param("id", code)
                        .update(),
                "consommation du code");
    }
}
