package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SqlSupportTest {

    private static DuplicateKeyException duplicate(String message) {
        return new DuplicateKeyException("doublon", new SQLException(message, "23505"));
    }

    @Test
    @DisplayName("AUTH-02 : la contrainte violée est reconnue quelle que soit la langue du serveur PostgreSQL")
    void auth02ConstraintIsRecognisedInAnyLanguage() {
        assertThat(SqlSupport.violates(
                        duplicate("ERROR: duplicate key value violates unique constraint \"users_username_key\""),
                        "users_username_key"))
                .isTrue();
        assertThat(SqlSupport.violates(
                        duplicate(
                                "ERREUR: la valeur d'une clé dupliquée rompt la contrainte unique « users_username_key »"),
                        "users_username_key"))
                .isTrue();
    }

    @Test
    @DisplayName("AUTH-02 : une autre contrainte, même de nom voisin, n'est pas prise pour celle-ci")
    void auth02OtherConstraintIsNotMistaken() {
        assertThat(SqlSupport.violates(
                        duplicate("ERROR: duplicate key value violates unique constraint \"users_username_key_old\""),
                        "users_username_key"))
                .isFalse();
        assertThat(SqlSupport.violates(
                        duplicate("ERROR: duplicate key value violates unique constraint \"users_verified_email\""),
                        "users_username_key"))
                .isFalse();
    }
}
