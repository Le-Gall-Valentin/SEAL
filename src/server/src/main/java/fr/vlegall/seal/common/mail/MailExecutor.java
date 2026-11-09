package fr.vlegall.seal.common.mail;

/**
 * Exécute l'envoi des courriels (ADR 0019, point 8). En production, {@link BoundedMailExecutor} les envoie hors de la
 * requête ; les tests d'intégration les envoient sur le fil appelant pour les vérifier sans attendre.
 */
@FunctionalInterface
public interface MailExecutor {

    /**
     * Programme l'envoi.
     *
     * @throws java.util.concurrent.RejectedExecutionException si la file d'envoi est pleine (A06)
     */
    void execute(Runnable sending);
}
