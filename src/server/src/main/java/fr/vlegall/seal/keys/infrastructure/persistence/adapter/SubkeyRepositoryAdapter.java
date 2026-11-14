package fr.vlegall.seal.keys.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@code subkeys} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class SubkeyRepositoryAdapter implements SubkeyRepository {

    private final JdbcClient jdbc;

    public SubkeyRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ADR 0025, point 15 : verrou consultatif transactionnel, libéré au commit ou à l'annulation. Hors transaction, il
     * tomberait aussitôt : {@code MANDATORY} le refuse plutôt que de laisser passer une course.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAccount(UserId user) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "subkeys:" + user.value())
                .query(row -> {});
    }

    @Override
    public int countLive(UserId user, Instant at) {
        return jdbc.sql("SELECT count(*) FROM subkeys WHERE user_id = :user AND expires_at > :at")
                .param("user", user.value())
                .param("at", Timestamp.from(at))
                .query(Integer.class)
                .single();
    }

    /** KEY-05 : la clé primaire {@code ek_id} refuse un ekId repris ; aucune erreur ne fait échouer la transaction. */
    @Override
    public boolean save(SubkeyBinding binding, byte[] signature) {
        return jdbc.sql("INSERT INTO subkeys"
                                + " (ek_id, user_id, cert_serial, kem, ek_public, created_at, expires_at, binding_signature)"
                                + " VALUES (:ekId, :user, :certSerial, :kem, :ekPublic, :createdAt, :expiresAt, :signature)"
                                + " ON CONFLICT (ek_id) DO NOTHING")
                        .param("ekId", binding.ekId())
                        .param("user", binding.user().value())
                        .param("certSerial", binding.certSerial())
                        .param("kem", binding.kem())
                        .param("ekPublic", binding.ekPublic())
                        .param("createdAt", Timestamp.from(binding.createdAt()))
                        .param("expiresAt", Timestamp.from(binding.expiresAt()))
                        .param("signature", signature)
                        .update()
                == 1;
    }
}
