package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import fr.vlegall.seal.identity.infrastructure.persistence.entity.UserEntity;
import fr.vlegall.seal.identity.infrastructure.persistence.repository.UserJpaRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code users}. Les courriels se comparent en {@code citext} explicite : un paramètre texte ferait comparer en
 * {@code text}, en tenant compte de la casse (AUTH-15).
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    /** AUTH-02 : contrainte d'unicité de la colonne {@code username}, nommée par PostgreSQL. */
    static final String USERNAME_CONSTRAINT = "users_username_key";

    /** AUTH-15 : index partiel qui réserve une adresse au seul compte actif (ADR 0019). */
    static final String VERIFIED_EMAIL_INDEX = "users_verified_email";

    private final UserJpaRepository jpa;
    private final JdbcClient jdbc;

    public UserRepositoryAdapter(UserJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public UserId createPending(Username username, DisplayName displayName, EmailAddress email) {
        try {
            return new UserId(jdbc.sql("INSERT INTO users (username, display_name, email) "
                            + "VALUES (:username, :displayName, CAST(:email AS citext)) RETURNING id")
                    .param("username", username.value())
                    .param("displayName", displayName.value())
                    .param("email", email.value())
                    .query(UUID.class)
                    .single());
        } catch (DuplicateKeyException e) {
            if (violates(e, USERNAME_CONSTRAINT)) {
                throw new IdentityException.UsernameTaken();
            }
            throw e;
        }
    }

    @Override
    public Optional<Account> findById(UserId id) {
        return jpa.findById(id.value()).map(UserRepositoryAdapter::toAccount);
    }

    @Override
    public Optional<Account> lockById(UserId id) {
        return jpa.findForUpdate(id.value()).map(UserRepositoryAdapter::toAccount);
    }

    @Override
    public void activate(UserId id, Instant verifiedAt) {
        try {
            jdbc.sql("UPDATE users SET status = 'ACTIVE', email_verified_at = :verifiedAt "
                            + "WHERE id = :id AND status = 'PENDING_EMAIL'")
                    .param("verifiedAt", OffsetDateTime.ofInstant(verifiedAt, ZoneOffset.UTC))
                    .param("id", id.value())
                    .update();
        } catch (DuplicateKeyException e) {
            if (violates(e, VERIFIED_EMAIL_INDEX)) {
                throw new IdentityException.CodeExpired();
            }
            throw e;
        }
    }

    @Override
    public void changeEmail(UserId id, EmailAddress email) {
        jdbc.sql("UPDATE users SET email = CAST(:email AS citext) WHERE id = :id AND status = 'PENDING_EMAIL'")
                .param("email", email.value())
                .param("id", id.value())
                .update();
    }

    @Override
    public boolean activeAccountOwns(EmailAddress email) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM users WHERE email = CAST(:email AS citext) AND status = 'ACTIVE')")
                .param("email", email.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean exists(Username username) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE username = :username)")
                .param("username", username.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public Set<String> takenAmong(Collection<Username> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT username FROM users WHERE username IN (:usernames)")
                .param("usernames", candidates.stream().map(Username::value).toList())
                .query(String.class)
                .list());
    }

    static Account toAccount(UserEntity entity) {
        String email = entity.getEmail();
        return new Account(
                new UserId(entity.getId()),
                new Username(entity.getUsername()),
                new DisplayName(entity.getDisplayName()),
                email == null ? null : new EmailAddress(email),
                AccountStatus.valueOf(entity.getStatus()));
    }

    /** Le nom de la contrainte figure dans le message de PostgreSQL, même sans le détail retiré par SEC-21. */
    private static boolean violates(DuplicateKeyException exception, String constraint) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause.getMessage();
        return message != null && message.contains("\"" + constraint + "\"");
    }
}
