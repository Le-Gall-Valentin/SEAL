package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** AUTH-23, ADR 0045 (point 5) : suppression comptée d'une ligne de session, validée dans sa propre transaction. */
@SealIntegrationTest
class JdbcStoredSessionsIT {

    @Autowired
    private StoredSessions stored;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    @Autowired
    private PlatformTransactionManager transactions;

    private <S extends Session> String savedSession(SessionRepository<S> repository) {
        S session = repository.createSession();
        repository.save(session);
        return session.getId();
    }

    @Test
    @DisplayName("AUTH-23, ADR 0045 : seule la première suppression d'une ligne la compte")
    void auth23OnlyTheFirstDeletionCounts() {
        String id = savedSession(sessions);

        assertThat(stored.delete(id)).isTrue();
        assertThat(stored.delete(id)).isFalse();
        assertThat(sessions.findById(id)).isNull();
    }

    @Test
    @DisplayName("AUTH-23, ADR 0045 : la suppression tient même si la transaction de l'appelant est annulée ensuite")
    void auth23DeletionSurvivesTheCallerRollback() {
        String id = savedSession(sessions);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(stored.delete(id)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(sessions.findById(id))
                .as("ancienne session ranimée par l'annulation")
                .isNull();
    }
}
