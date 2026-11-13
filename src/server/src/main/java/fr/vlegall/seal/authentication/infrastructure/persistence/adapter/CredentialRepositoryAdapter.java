package fr.vlegall.seal.authentication.infrastructure.persistence.adapter;

import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code user_credentials} par {@link JdbcClient} : une ligne, écrite dans la transaction en cours. */
@Component
public class CredentialRepositoryAdapter implements CredentialRepository {

    private final JdbcClient jdbc;

    public CredentialRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UserId user, String passwordHash) {
        jdbc.sql("INSERT INTO user_credentials (user_id, password_hash) VALUES (:user, :hash)")
                .param("user", user.value())
                .param("hash", passwordHash)
                .update();
    }

    @Override
    public boolean changeHash(UserId user, String passwordHash) {
        return jdbc.sql("UPDATE user_credentials SET password_hash = :hash WHERE user_id = :user")
                        .param("user", user.value())
                        .param("hash", passwordHash)
                        .update()
                == 1;
    }

    @Override
    public boolean lock(UserId user) {
        return jdbc.sql("SELECT 1 FROM user_credentials WHERE user_id = :user FOR UPDATE")
                .param("user", user.value())
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public Optional<String> findHash(UserId user) {
        return jdbc.sql("SELECT password_hash FROM user_credentials WHERE user_id = :user")
                .param("user", user.value())
                .query(String.class)
                .optional();
    }
}
