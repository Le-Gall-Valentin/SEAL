package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code email_codes} par {@link JdbcClient} : le haché seul est stocké (DATA-04). */
@Component
public class EmailCodeRepositoryAdapter implements EmailCodeRepository {

    private final JdbcClient jdbc;

    public EmailCodeRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt) {
        jdbc.sql("UPDATE email_codes SET consumed_at = :now WHERE user_id = :user AND consumed_at IS NULL")
                .param("now", utc(issuedAt))
                .param("user", user.value())
                .update();
        jdbc.sql("INSERT INTO email_codes (user_id, purpose, code_hash, expires_at) "
                        + "VALUES (:user, 'VERIFY_EMAIL', :hash, :expiresAt)")
                .param("user", user.value())
                .param("hash", codeHash)
                .param("expiresAt", utc(expiresAt))
                .update();
    }

    @Override
    public Optional<Instant> latestExpiry(UserId user) {
        return jdbc.sql("SELECT expires_at FROM email_codes WHERE user_id = :user AND consumed_at IS NULL "
                        + "ORDER BY id DESC LIMIT 1")
                .param("user", user.value())
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant);
    }

    @Override
    public Optional<LiveCode> findLive(UserId user, Instant now) {
        return jdbc.sql("SELECT id, code_hash FROM email_codes WHERE user_id = :user AND consumed_at IS NULL "
                        + "AND expires_at > :now AND attempts < :maxAttempts ORDER BY id DESC LIMIT 1")
                .param("user", user.value())
                .param("now", utc(now))
                .param("maxAttempts", CodePolicy.MAX_ATTEMPTS)
                .query((row, number) -> new LiveCode(row.getObject("id", UUID.class), row.getBytes("code_hash")))
                .optional();
    }

    @Override
    public void countFailedAttempt(UUID code) {
        jdbc.sql("UPDATE email_codes SET attempts = attempts + 1 WHERE id = :id")
                .param("id", code)
                .update();
    }

    @Override
    public void consume(UUID code, Instant now) {
        jdbc.sql("UPDATE email_codes SET consumed_at = :now WHERE id = :id AND consumed_at IS NULL")
                .param("now", utc(now))
                .param("id", code)
                .update();
    }

    @Override
    public void retireOthersForAddress(EmailAddress email, UserId owner, Instant now) {
        jdbc.sql("UPDATE email_codes SET consumed_at = :now WHERE consumed_at IS NULL AND user_id IN ("
                        + "SELECT id FROM users WHERE email = CAST(:email AS citext) AND status = 'PENDING_EMAIL' "
                        + "AND id <> :owner)")
                .param("now", utc(now))
                .param("email", email.value())
                .param("owner", owner.value())
                .update();
    }

    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
