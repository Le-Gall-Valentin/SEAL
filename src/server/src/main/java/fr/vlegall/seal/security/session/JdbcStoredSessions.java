package fr.vlegall.seal.security.session;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AUTH-23, ADR 0045 : suppression comptée d'une ligne de Spring Session JDBC. La suppression est atomique : de deux
 * requêtes qui la tentent, une seule la compte. Elle est validée aussitôt, dans sa propre transaction, comme les
 * écritures de Spring Session : un appelant transactionnel annulé ne ranimerait pas l'ancienne session à côté de la
 * nouvelle. Les attributs suivent par la clé étrangère ({@code ON DELETE CASCADE}). La table est celle par défaut de
 * Spring Session, créée par Flyway (V1).
 */
@Component
final class JdbcStoredSessions implements StoredSessions {

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;

    JdbcStoredSessions(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean delete(String sessionId) {
        Integer deleted = ownTransaction.execute(status -> jdbc.sql("DELETE FROM SPRING_SESSION WHERE SESSION_ID = :id")
                .param("id", sessionId)
                .update());
        return deleted != null && deleted > 0;
    }
}
