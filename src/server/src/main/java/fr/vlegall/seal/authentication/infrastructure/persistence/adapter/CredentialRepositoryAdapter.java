package fr.vlegall.seal.authentication.infrastructure.persistence.adapter;

import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.UserId;
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
}
