package fr.vlegall.seal.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Adaptateurs de persistance d'{@code identity} : une écriture qui ne touche aucune ligne échoue (échec fermé). */
@SealIntegrationTest
class IdentityPersistenceIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private EmailCodeRepository codes;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private UserId insertActive(String username) {
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name, email, status) "
                        + "VALUES (?, 'Nom', CAST(? AS citext), 'ACTIVE') RETURNING id")
                .param(username)
                .param(username + "@exemple.ca")
                .query(UUID.class)
                .single());
    }

    @Test
    @DisplayName("AUTH-14 : activer un compte qui n'est pas en attente échoue au lieu de passer pour un succès")
    void auth14ActivatingANonPendingAccountFails() {
        UserId active = insertActive("persistance.un");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> users.activate(active, Instant.now())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AUTH-17 : changer l'adresse d'un compte qui n'est pas en attente échoue")
    void auth17ChangingTheEmailOfANonPendingAccountFails() {
        UserId active = insertActive("persistance.deux");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> users.changeEmail(
                        active, EmailAddress.parse("autre@exemple.ca").orElseThrow())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AUTH-14 : consommer un code déjà consommé, ou inconnu, échoue")
    void auth14ConsumingAConsumedCodeFails() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> codes.consume(unknown, Instant.now())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> codes.countFailedAttempt(unknown)))
                .isInstanceOf(IllegalStateException.class);
    }
}
