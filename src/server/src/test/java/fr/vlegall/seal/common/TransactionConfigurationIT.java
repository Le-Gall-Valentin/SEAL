package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR 0020 : seule une transaction réelle lie une connexion au fil. */
@SealIntegrationTest
class TransactionConfigurationIT {

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("AUTH-13 : le gestionnaire de transactions ne synchronise que les transactions réelles (ADR 0020)")
    void auth13OnlyActualTransactionsAreSynchronised() {
        assertThat(transactions)
                .isInstanceOfSatisfying(
                        AbstractPlatformTransactionManager.class,
                        manager -> assertThat(manager.getTransactionSynchronization())
                                .isEqualTo(AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION));
    }

    @ParameterizedTest(name = "propagation {0}")
    @ValueSource(ints = {TransactionTemplate.PROPAGATION_NEVER, TransactionTemplate.PROPAGATION_SUPPORTS})
    @DisplayName("AUTH-13 : une portée sans transaction réelle qui lit la base ne garde aucune connexion (A06)")
    void auth13ScopeWithoutActualTransactionHoldsNoConnection(int propagation) {
        TransactionTemplate scope = new TransactionTemplate(transactions);
        scope.setPropagationBehavior(propagation);
        AtomicBoolean bound = new AtomicBoolean(true);

        scope.executeWithoutResult(status -> {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            bound.set(!TransactionSynchronizationManager.getResourceMap().isEmpty());
        });

        assertThat(bound).isFalse();
    }

    @Test
    @DisplayName("AUTH-13 : une transaction réelle garde sa connexion jusqu'à sa fin, comme avant")
    void auth13ActualTransactionKeepsItsConnection() {
        AtomicBoolean bound = new AtomicBoolean(false);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            bound.set(!TransactionSynchronizationManager.getResourceMap().isEmpty());
        });

        assertThat(bound).isTrue();
    }
}
