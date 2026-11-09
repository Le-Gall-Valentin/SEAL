package fr.vlegall.seal.common;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/**
 * Le gestionnaire de transactions ne synchronise que les transactions réelles (AUTH-13, A06, ADR 0020). Hors de toute
 * transaction, une portée {@code NEVER}, {@code SUPPORTS} ou {@code NOT_SUPPORTED} ne lie alors aucune connexion au
 * fil : chaque accès à la base la rend aussitôt, et une attente (le sémaphore d'Argon2id, ADR 0019 point 3) ne retient
 * aucune connexion du pool. Une portée {@code NOT_SUPPORTED} ouverte dans une transaction libère le fil, mais la
 * connexion de la transaction suspendue reste empruntée jusqu'à sa reprise : une attente doit se faire hors de toute
 * transaction, ce que {@code NEVER} impose.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {

    @Bean
    TransactionManagerCustomizer<AbstractPlatformTransactionManager> synchronizeActualTransactionsOnly() {
        return manager -> manager.setTransactionSynchronization(
                AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
    }
}
