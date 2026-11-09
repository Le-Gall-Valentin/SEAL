package fr.vlegall.seal.common;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/**
 * ADR 0020 : le gestionnaire de transactions ne synchronise que les transactions réelles. Une portée {@code NEVER},
 * {@code SUPPORTS} ou {@code NOT_SUPPORTED} ne lie alors aucune connexion au fil : chaque accès à la base la rend
 * aussitôt, et une attente (le sémaphore d'Argon2id, ADR 0019 point 3) ne retient aucune connexion du pool.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {

    @Bean
    TransactionManagerCustomizer<AbstractPlatformTransactionManager> synchronizeActualTransactionsOnly() {
        return manager -> manager.setTransactionSynchronization(
                AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
    }
}
