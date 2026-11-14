package fr.vlegall.seal.keys.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code key_vaults} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class VaultRepositoryAdapter implements VaultRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public VaultRepositoryAdapter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Vault> find(UserId user) {
        return jdbc.sql("SELECT version, kek_salt, vdk_nonce, wrapped_vdk, blob_nonce, blob FROM key_vaults"
                        + " WHERE user_id = :user")
                .param("user", user.value())
                .query((row, number) -> new Vault(
                        row.getLong("version"),
                        row.getBytes("kek_salt"),
                        row.getBytes("vdk_nonce"),
                        row.getBytes("wrapped_vdk"),
                        row.getBytes("blob_nonce"),
                        row.getBytes("blob")))
                .optional();
    }

    /**
     * ADR 0025, point 6 : la version 1 ne s'écrit que s'il n'y a pas de coffre ({@code ON CONFLICT DO NOTHING}), les
     * suivantes que sur la version précédente ({@code WHERE version = :previous}). De deux écritures concurrentes de la
     * même version, une seule passe.
     */
    @Override
    public boolean save(UserId user, Vault vault) {
        String sql = vault.version() == 1
                ? "INSERT INTO key_vaults (user_id, version, kek_salt, vdk_nonce, wrapped_vdk, blob_nonce, blob, updated_at)"
                        + " VALUES (:user, :version, :salt, :nonceK, :wrappedVdk, :nonceB, :blob, :now)"
                        + " ON CONFLICT (user_id) DO NOTHING"
                : "UPDATE key_vaults SET version = :version, kek_salt = :salt, vdk_nonce = :nonceK,"
                        + " wrapped_vdk = :wrappedVdk, blob_nonce = :nonceB, blob = :blob, updated_at = :now"
                        + " WHERE user_id = :user AND version = :previous";
        return jdbc.sql(sql)
                        .param("user", user.value())
                        .param("version", vault.version())
                        .param("previous", vault.version() - 1)
                        .param("salt", vault.salt())
                        .param("nonceK", vault.nonceK())
                        .param("wrappedVdk", vault.wrappedVdk())
                        .param("nonceB", vault.nonceB())
                        .param("blob", vault.blob())
                        .param("now", Timestamp.from(clock.instant()))
                        .update()
                == 1;
    }
}
