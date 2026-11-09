package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR 0020 : seule une transaction réelle lie une connexion au fil et la garde empruntée au pool. */
@SealIntegrationTest
class TransactionConfigurationIT {

    /** État observé dans une portée, juste après une lecture de la base. */
    private record Observation(boolean resourceBound, int activeConnections) {}

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HikariDataSource dataSource;

    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }

    private TransactionTemplate scope(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(propagation);
        return template;
    }

    /** Lit la base dans la portée, puis observe ce qui reste lié au fil et emprunté au pool. */
    private Observation readThenObserve(TransactionTemplate template, Consumer<Void> read) {
        return template.execute(status -> {
            read.accept(null);
            return new Observation(
                    !TransactionSynchronizationManager.getResourceMap().isEmpty(),
                    dataSource.getHikariPoolMXBean().getActiveConnections());
        });
    }

    private void readByJdbc(Void unused) {
        jdbc.sql("SELECT 1").query(Integer.class).single();
    }

    private void readByJpa(Void unused) {
        entityManager.createNativeQuery("SELECT 1").getSingleResult();
    }

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
    @ValueSource(
            ints = {
                TransactionTemplate.PROPAGATION_NEVER,
                TransactionTemplate.PROPAGATION_SUPPORTS,
                TransactionTemplate.PROPAGATION_NOT_SUPPORTED
            })
    @DisplayName("AUTH-13 : une portée sans transaction réelle qui lit la base en JDBC ne garde aucune connexion (A06)")
    void auth13ScopeWithoutActualTransactionHoldsNoJdbcConnection(int propagation) {
        assertThat(readThenObserve(scope(propagation), this::readByJdbc)).isEqualTo(new Observation(false, 0));
    }

    @ParameterizedTest(name = "propagation {0}")
    @ValueSource(
            ints = {
                TransactionTemplate.PROPAGATION_NEVER,
                TransactionTemplate.PROPAGATION_SUPPORTS,
                TransactionTemplate.PROPAGATION_NOT_SUPPORTED
            })
    @DisplayName("AUTH-13 : une portée sans transaction réelle qui lit la base par JPA ne garde aucune connexion (A06)")
    void auth13ScopeWithoutActualTransactionHoldsNoJpaConnection(int propagation) {
        assertThat(readThenObserve(scope(propagation), this::readByJpa)).isEqualTo(new Observation(false, 0));
    }

    @Test
    @DisplayName("AUTH-13 : NOT_SUPPORTED dans une transaction libère le fil, pas la connexion suspendue (ADR 0020)")
    void auth13NestedNotSupportedKeepsTheSuspendedConnection() {
        Observation inner = new TransactionTemplate(transactions).execute(status -> {
            readByJdbc(null);
            return readThenObserve(scope(TransactionTemplate.PROPAGATION_NOT_SUPPORTED), this::readByJdbc);
        });

        assertThat(inner).isEqualTo(new Observation(false, 1));
    }

    @Test
    @DisplayName("AUTH-13 : une transaction réelle garde sa connexion jusqu'à sa fin, comme avant")
    void auth13ActualTransactionKeepsItsConnection() {
        assertThat(readThenObserve(new TransactionTemplate(transactions), this::readByJdbc))
                .isEqualTo(new Observation(true, 1));
    }
}
