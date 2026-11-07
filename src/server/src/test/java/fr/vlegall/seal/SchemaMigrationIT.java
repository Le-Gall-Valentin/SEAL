package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** La migration V1 (schéma de la section 13 du CDC) s'applique sur PostgreSQL 18. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID insertUser(String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name) VALUES (?, ?) RETURNING id", UUID.class, username, "Nom");
    }

    @Test
    @DisplayName("DATA-01 : le schéma vient de la migration Flyway V1, validée par Hibernate")
    void data01SchemaComesFromFlyway() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
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
}
