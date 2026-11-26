package fr.vlegall.seal.ws.domain.model;

import fr.vlegall.seal.common.UserId;

/** Une connexion WebSocket ouverte d'un compte, liée à sa session HTTP (TRN-12). */
public interface Connection {

    /** Identifiant propre à la connexion. */
    String id();

    /** Le compte de la session. */
    UserId user();

    /** L'identifiant de la session HTTP à laquelle la connexion est liée. */
    String sessionId();

    /** Envoie une trame texte ; une connexion déjà fermée l'ignore. */
    void send(String text);

    /** Ferme la connexion avec le code de sa cause. */
    void close(CloseReason reason);
}
