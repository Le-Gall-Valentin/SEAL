package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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

    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
