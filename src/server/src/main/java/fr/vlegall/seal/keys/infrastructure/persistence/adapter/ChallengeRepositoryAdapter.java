package fr.vlegall.seal.keys.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code key_challenges} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class ChallengeRepositoryAdapter implements ChallengeRepository {

    private final JdbcClient jdbc;

    public ChallengeRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** ADR 0025, point 8 : une ligne par compte (index unique de la V5), remplacée d'un coup. */
    @Override
    public void renew(UserId user, byte[] hash, Instant expiresAt) {
        jdbc.sql("INSERT INTO key_challenges (user_id, challenge_hash, expires_at) VALUES (:user, :hash, :expiresAt)"
                        + " ON CONFLICT (user_id) DO UPDATE SET challenge_hash = EXCLUDED.challenge_hash,"
                        + " expires_at = EXCLUDED.expires_at, consumed_at = NULL")
                .param("user", user.value())
                .param("hash", hash)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    /** Mise à jour conditionnelle : de deux requêtes simultanées, une seule consomme le défi (KEY-03). */
    @Override
    public boolean consume(UserId user, byte[] hash, Instant at) {
        return jdbc.sql("UPDATE key_challenges SET consumed_at = :at WHERE user_id = :user AND challenge_hash = :hash"
                                + " AND consumed_at IS NULL AND expires_at > :at")
                        .param("user", user.value())
                        .param("hash", hash)
                        .param("at", Timestamp.from(at))
                        .update()
                == 1;
    }
}
