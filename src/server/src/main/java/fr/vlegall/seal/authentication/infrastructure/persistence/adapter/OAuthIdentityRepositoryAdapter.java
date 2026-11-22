package fr.vlegall.seal.authentication.infrastructure.persistence.adapter;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthException;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code oauth_identities} par {@link JdbcClient}, dans la transaction en cours. */
@Component
public class OAuthIdentityRepositoryAdapter implements OAuthIdentityRepository {

    private final JdbcClient jdbc;

    public OAuthIdentityRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserId> findUser(OAuthProvider provider, String subject) {
        return jdbc.sql("SELECT user_id FROM oauth_identities WHERE provider = :provider AND subject = :subject")
                .param("provider", provider.name())
                .param("subject", subject)
                .query(UUID.class)
                .optional()
                .map(UserId::new);
    }

    @Override
    public boolean isLinked(UserId user, OAuthProvider provider) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM oauth_identities "
                        + "WHERE user_id = :user AND provider = :provider)")
                .param("user", user.value())
                .param("provider", provider.name())
                .query(Boolean.class)
                .single());
    }

    @Override
    public List<OAuthProvider> linkedProviders(UserId user) {
        return jdbc
                .sql("SELECT provider FROM oauth_identities WHERE user_id = :user")
                .param("user", user.value())
                .query(String.class)
                .list()
                .stream()
                .map(OAuthProvider::valueOf)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Les deux contraintes d'unicité, {@code (provider, subject)} et {@code (user_id, provider)}, signifient la même
     * chose pour l'appelant : {@code ON CONFLICT DO NOTHING} les couvre sans lire le message de PostgreSQL.
     */
    @Override
    public void link(UserId user, ExternalIdentity identity, Instant linkedAt) {
        int rows = jdbc.sql("""
                        INSERT INTO oauth_identities (user_id, provider, subject, email_seen, linked_at)
                        VALUES (:user, :provider, :subject, :email, :linkedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("user", user.value())
                .param("provider", identity.provider().name())
                .param("subject", identity.subject())
                .param("email", identity.email())
                .param("linkedAt", OffsetDateTime.ofInstant(linkedAt, ZoneOffset.UTC))
                .update();
        if (rows == 0) {
            throw new OAuthException.AlreadyLinked();
        }
    }
}
