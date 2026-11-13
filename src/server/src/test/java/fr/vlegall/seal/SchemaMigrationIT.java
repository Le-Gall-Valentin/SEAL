package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Les migrations V1 et V2 (schéma de la section 13 du CDC, tables par contexte) s'appliquent sur PostgreSQL 18. */
@SealIntegrationTest
@Transactional
class SchemaMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private UUID insertUser(String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name) VALUES (?, ?) RETURNING id", UUID.class, username, "Nom");
    }

    private UUID insertUser(String username, String email, String status) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email, status) VALUES (?, 'Nom', ?, ?) RETURNING id",
                UUID.class,
                username,
                email,
                status);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? "
                        + "ORDER BY ordinal_position",
                String.class,
                table);
    }

    @Test
    @DisplayName("DATA-01 : le schéma vient des migrations Flyway V1 à V3, validées par Hibernate")
    void data01SchemaComesFromFlyway() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                                + "AND table_name IN ('users', 'messages', 'spring_session', 'spring_session_attributes')",
                        Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("DATA-03 : les identifiants serveur sont des UUIDv7 natifs de PostgreSQL 18")
    void data03ServerIdentifiersAreUuidV7() {
        UUID id = insertUser("alice");

        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("AUTH-03 : le déclencheur PostgreSQL rejette toute modification de l'identifiant")
    void auth03UsernameCannotBeUpdated() {
        UUID id = insertUser("bob");

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET username = 'robert' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("username is immutable");
    }

    @Test
    @DisplayName("AUTH-01 : la base refuse un identifiant hors du format imposé")
    void auth01DatabaseRejectsAnInvalidUsername() {
        assertThatThrownBy(() -> insertUser("Not.Valid"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("users_username_check");
    }

    @Test
    @DisplayName("DATA-01 : chaque contexte possède ses tables, avec les colonnes de la section 13 (ADR 0009)")
    void data01EachContextOwnsItsTables() {
        assertThat(columnsOf("users"))
                .containsExactly(
                        "id",
                        "username",
                        "display_name",
                        "email",
                        "email_verified_at",
                        "status",
                        "created_at",
                        "deleted_at");
        assertThat(columnsOf("email_codes"))
                .containsExactly(
                        "id", "user_id", "purpose", "code_hash", "target", "attempts", "expires_at", "consumed_at");
        assertThat(columnsOf("user_credentials")).containsExactly("user_id", "password_hash", "version");
        assertThat(columnsOf("password_reset_tokens"))
                .containsExactly("id", "user_id", "token_hash", "expires_at", "consumed_at");
        assertThat(columnsOf("totp_factors")).containsExactly("user_id", "secret_enc", "enabled_at", "last_step");
        assertThat(columnsOf("key_challenges"))
                .containsExactly("id", "user_id", "challenge_hash", "expires_at", "consumed_at");
        assertThat(columnsOf("one_time_codes")).isEmpty();
    }

    @Test
    @DisplayName("AUTH-15 : plusieurs comptes en attente partagent une adresse, un seul compte actif la possède")
    void auth15OnlyAnActiveAccountOwnsItsAddress() {
        insertUser("alice", "Alice@Exemple.ca", "PENDING_EMAIL");
        insertUser("alice2", "alice@exemple.ca", "PENDING_EMAIL");
        insertUser("alice3", "alice@exemple.ca", "ACTIVE");

        assertThatThrownBy(() -> insertUser("alice4", "ALICE@exemple.ca", "ACTIVE"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("users_verified_email");
    }

    @Test
    @DisplayName("AUTH-14 : la base refuse un code de courriel d'un autre usage que la vérification ou le changement")
    void auth14EmailCodesOnlyServeTheEmail() {
        UUID id = insertUser("carol");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO email_codes (user_id, purpose, code_hash, expires_at) "
                                + "VALUES (?, 'RESET_PASSWORD', '\\x00', now())",
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("email_codes_purpose_check");
    }

    /**
     * La V2 recopie les secrets et les codes que la V1 rangeait dans {@code users} et {@code one_time_codes}. Le test
     * migre une base neuve jusqu'à la V1, y écrit, puis la migre jusqu'à la V2.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DATA-01 : la V2 recopie dans les tables des contextes les secrets et les codes de la V1 (ADR 0009)")
    void data01VersionTwoCopiesTheVersionOneData() {
        jdbc.execute("DROP DATABASE IF EXISTS seal_upgrade");
        jdbc.execute("CREATE DATABASE seal_upgrade");
        HikariDataSource main = (HikariDataSource) dataSource;
        DriverManagerDataSource upgrade = new DriverManagerDataSource(
                main.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/seal_upgrade$1"),
                main.getUsername(),
                main.getPassword());
        try {
            Flyway.configure()
                    .dataSource(upgrade)
                    .locations("classpath:db/migration")
                    .target("1")
                    .load()
                    .migrate();
            JdbcTemplate v1 = new JdbcTemplate(upgrade);
            UUID user = v1.queryForObject(
                    "INSERT INTO users (username, display_name, email, password_hash, totp_secret_enc, totp_enabled_at,"
                            + " totp_last_step) VALUES ('dave', 'Dave', 'dave@exemple.ca', '{argon2}h', '\\x0102',"
                            + " now(), 42) RETURNING id",
                    UUID.class);
            for (String purpose : List.of("VERIFY_EMAIL", "CHANGE_EMAIL", "RESET_PASSWORD", "KEY_CHALLENGE")) {
                v1.update(
                        "INSERT INTO one_time_codes (user_id, purpose, code_hash, target, attempts, expires_at) "
                                + "VALUES (?, ?, decode(md5(?), 'hex'), 'new@exemple.ca', 2, now())",
                        user,
                        purpose,
                        purpose);
            }

            Flyway.configure()
                    .dataSource(upgrade)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(v1.queryForObject(
                            "SELECT password_hash FROM user_credentials WHERE user_id = ?", String.class, user))
                    .isEqualTo("{argon2}h");
            Map<String, Object> totp = v1.queryForMap("SELECT * FROM totp_factors WHERE user_id = ?", user);
            assertThat(totp.get("last_step")).isEqualTo(42L);
            assertThat(totp.get("enabled_at")).isNotNull();
            assertThat(v1.queryForList(
                            "SELECT purpose FROM email_codes WHERE user_id = ? AND attempts = 2 "
                                    + "AND target = 'new@exemple.ca' ORDER BY purpose",
                            String.class,
                            user))
                    .containsExactly("CHANGE_EMAIL", "VERIFY_EMAIL");
            assertThat(v1.queryForObject(
                            "SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, user))
                    .isEqualTo(1);
            assertThat(v1.queryForObject("SELECT count(*) FROM key_challenges WHERE user_id = ?", Integer.class, user))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS seal_upgrade WITH (FORCE)");
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("AUTH-70 : la V3 vide les sessions, aucune ne portant de version du mot de passe (ADR 0023)")
    void auth70VersionThreeDropsTheSessionsOpenedBefore() {
        jdbc.execute("DROP DATABASE IF EXISTS seal_upgrade");
        jdbc.execute("CREATE DATABASE seal_upgrade");
        HikariDataSource main = (HikariDataSource) dataSource;
        DriverManagerDataSource upgrade = new DriverManagerDataSource(
                main.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/seal_upgrade$1"),
                main.getUsername(),
                main.getPassword());
        try {
            Flyway.configure()
                    .dataSource(upgrade)
                    .locations("classpath:db/migration")
                    .target("2")
                    .load()
                    .migrate();
            JdbcTemplate v2 = new JdbcTemplate(upgrade);
            UUID user = v2.queryForObject(
                    "INSERT INTO users (username, display_name, email) VALUES ('erin', 'Erin', 'erin@exemple.ca')"
                            + " RETURNING id",
                    UUID.class);
            v2.update("INSERT INTO user_credentials (user_id, password_hash) VALUES (?, '{argon2}h')", user);
            v2.update(
                    "INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,"
                            + " max_inactive_interval, expiry_time, principal_name) VALUES ('p', 's', 0, 0, 1800, 9999999999999,"
                            + " ?)",
                    user.toString());
            v2.update("INSERT INTO spring_session_attributes VALUES ('p', 'SPRING_SECURITY_CONTEXT', '\\x00')");

            Flyway.configure()
                    .dataSource(upgrade)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(v2.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
                    .isZero();
            assertThat(v2.queryForObject("SELECT count(*) FROM spring_session_attributes", Integer.class))
                    .isZero();
            assertThat(v2.queryForObject("SELECT version FROM user_credentials WHERE user_id = ?", Long.class, user))
                    .isEqualTo(1L);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS seal_upgrade WITH (FORCE)");
        }
    }
}
