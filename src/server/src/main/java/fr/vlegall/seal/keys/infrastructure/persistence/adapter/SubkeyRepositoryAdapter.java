package fr.vlegall.seal.keys.infrastructure.persistence.adapter;

import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code subkeys} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class SubkeyRepositoryAdapter implements SubkeyRepository {

    private final JdbcClient jdbc;

    public SubkeyRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
