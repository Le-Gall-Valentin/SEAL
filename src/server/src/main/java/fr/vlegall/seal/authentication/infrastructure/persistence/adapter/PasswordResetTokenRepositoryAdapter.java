package fr.vlegall.seal.authentication.infrastructure.persistence.adapter;

import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code password_reset_tokens} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private static final String LIVE = "consumed_at IS NULL AND expires_at > :now";

    private final JdbcClient jdbc;

    public PasswordResetTokenRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void issue(UserId user, byte[] tokenHash, Instant expiresAt, Instant now) {
        consumeAll(user, now);
        jdbc.sql(
                        "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (:user, :hash, :expiresAt)")
                .param("user", user.value())
                .param("hash", tokenHash)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    @Override
    public Optional<LiveReset> findLive(byte[] tokenHash, Instant now) {
        return jdbc.sql("SELECT id, user_id, expires_at FROM password_reset_tokens WHERE token_hash = :hash AND "
                        + LIVE)
                .param("hash", tokenHash)
                .param("now", Timestamp.from(now))
                .query((row, number) -> new LiveReset(
                        row.getObject("id", UUID.class),
                        new UserId(row.getObject("user_id", UUID.class)),
                        row.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    @Override
    public boolean consume(UUID id, Instant now) {
        return jdbc.sql("UPDATE password_reset_tokens SET consumed_at = :now WHERE id = :id AND " + LIVE)
                        .param("id", id)
                        .param("now", Timestamp.from(now))
                        .update()
                == 1;
    }

    @Override
    public void consumeAll(UserId user, Instant now) {
        jdbc.sql("UPDATE password_reset_tokens SET consumed_at = :now WHERE user_id = :user AND " + LIVE)
                .param("user", user.value())
                .param("now", Timestamp.from(now))
                .update();
    }
}
