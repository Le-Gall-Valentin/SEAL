package fr.vlegall.seal.authentication.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.application.port.in.InitialPasswordUseCase;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SealIntegrationTest
class CredentialStorageIT {

    @Autowired
    private InitialPasswordUseCase initialPassword;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private UserId insertUser(String username) {
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name) VALUES (?, 'Nom') RETURNING id")
                .param(username)
                .query(UUID.class)
                .single());
    }

    private String storedHash(UserId user) {
        return jdbc.sql("SELECT password_hash FROM user_credentials WHERE user_id = ?")
                .param(user.value())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    @Test
    @DisplayName(
            "AUTH-13 : Argon2id tourne hors de toute transaction (A06) ; dans une transaction, le hachage est refusé")
    void a06PasswordIsHashedOutsideAnyTransaction() {
        assertThat(initialPassword.prepare("fjord-tuque-raquette", List.of("credential.zero")))
                .hasValueSatisfying(hash -> assertThat(hash).startsWith("{argon2}$argon2id$"));

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> initialPassword.prepare("fjord-tuque-raquette", List.of())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("AUTH-13 : le haché est écrit dans la transaction de l'appelant, annulé avec elle")
    void auth13CredentialIsWrittenInTheCallerTransaction() {
        String hash = initialPassword
                .prepare("fjord-tuque-raquette", List.of("credential.un"))
                .orElseThrow();
        UserId[] user = new UserId[1];
        transactions.executeWithoutResult(status -> {
            user[0] = insertUser("credential.un");
            initialPassword.store(user[0], hash);
            assertThat(storedHash(user[0])).isEqualTo(hash);
            status.setRollbackOnly();
        });

        assertThat(storedHash(user[0])).isNull();
    }

    @Test
    @DisplayName("AUTH-13 : hors d'une transaction, l'enregistrement du mot de passe est refusé")
    void auth13StoringRequiresATransaction() {
        UserId user = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> initialPassword.store(user, "{argon2}$argon2id$x"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("AUTH-13 : un mot de passe en clair passé pour un haché n'est jamais écrit")
    void auth13PlaintextIsNeverStored() {
        UserId[] user = new UserId[1];
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    user[0] = insertUser("credential.deux");
                    initialPassword.store(user[0], "fjord-tuque-raquette");
                }))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(storedHash(user[0])).isNull();
    }
}
