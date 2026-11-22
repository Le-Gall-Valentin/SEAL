package fr.vlegall.seal.authentication.infrastructure.persistence.adapter;

import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import java.util.OptionalLong;
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
        return jdbc.sql(
                                "UPDATE user_credentials SET password_hash = :hash, version = version + 1 WHERE user_id = :user")
                        .param("user", user.value())
                        .param("hash", passwordHash)
                        .update()
                == 1;
    }

    @Override
    public OptionalLong changeHashIfUnchanged(UserId user, String expectedHash, String newHash, boolean newVersion) {
        return jdbc.sql("UPDATE user_credentials SET password_hash = :hash, version = version + :increment "
                        + "WHERE user_id = :user AND password_hash = :expected RETURNING version")
                .param("hash", newHash)
                .param("increment", newVersion ? 1 : 0)
                .param("user", user.value())
                .param("expected", expectedHash)
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
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
    public boolean hasPassword(UserId user) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_credentials WHERE user_id = :user)")
                .param("user", user.value())
                .query(Boolean.class)
                .single());
    }

    @Override
    public Optional<StoredCredential> find(UserId user) {
        return jdbc.sql("SELECT password_hash, version FROM user_credentials WHERE user_id = :user")
                .param("user", user.value())
                .query((row, number) -> new StoredCredential(row.getString("password_hash"), row.getLong("version")))
                .optional();
    }
}
