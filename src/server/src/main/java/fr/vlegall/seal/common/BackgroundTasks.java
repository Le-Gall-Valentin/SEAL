package fr.vlegall.seal.common;

/**
 * Exécute un travail hors du fil de la requête, pour que la réponse n'en dépende pas (ADR 0022, mise à jour : temps de
 * réponse de la demande de réinitialisation). En production, {@link BoundedBackgroundTasks} ; les tests d'intégration
 * l'exécutent sur le fil appelant pour le vérifier sans attendre.
 */
@FunctionalInterface
public interface BackgroundTasks {

    /** Programme la tâche ; si la file est pleine, elle est abandonnée et un WARN est écrit (A06), sans exception. */
    void submit(Runnable task);
}
