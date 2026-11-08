package fr.vlegall.seal.security.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SealIntegrationTest
class DatabaseErrorDetailIT {

    private static final String EMAIL = "valentin.detail@exemple.ca";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    /** Deux comptes actifs ne peuvent partager une adresse (index partiel {@code users_verified_email}, ADR 0019). */
    private void insertUser(String username) {
        jdbc.sql("INSERT INTO users (username, display_name, email, status) VALUES (?, 'Valentin', ?, 'ACTIVE')")
                .params(username, EMAIL)
                .update();
    }

    @Test
    @DisplayName(
            "SEC-21 : une erreur de la base ne recopie pas la valeur en conflit, donc aucun courriel en clair dans les traces")
    void sec21DatabaseErrorsDoNotCarryTheConflictingValue() {
        DataAccessException failure = transactions.execute(status -> {
            status.setRollbackOnly();
            insertUser("detail.un");
            return catchThrowableOfType(DataAccessException.class, () -> insertUser("detail.deux"));
        });

        assertThat(failure).isNotNull().hasMessageContaining("users_verified_email");
        assertThat(failure.getMessage()).doesNotContain(EMAIL);
        assertThat(failure.getMostSpecificCause().getMessage()).doesNotContain(EMAIL);
    }
}
