package fr.vlegall.seal.ws.domain.port.out;

/**
 * TRN-13, ADR 0044 (décision 10) : exécute un envoi ou une fermeture hors du fil appelant, pour qu'un client lent
 * n'atteigne ni la requête HTTP qui annonce, ni la ronde, ni les autres connexions.
 */
@FunctionalInterface
public interface FrameDispatcher {

    /** Programme la tâche ; une file pleine l'abandonne avec un WARN (A06), sans exception. */
    void dispatch(Runnable task);
}
