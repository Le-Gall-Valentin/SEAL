package fr.vlegall.seal.ws.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;

/** Une connexion WebSocket ouverte d'un compte, liée à sa session HTTP (TRN-12). */
public interface Connection {

    /** Identifiant propre à la connexion. */
    String id();

    /** Le compte de la session. */
    UserId user();

    /** TRN-12 : la session HTTP à laquelle la connexion est liée, lue d'un seul coup. */
    SessionBinding binding();

    /** L'identifiant actuel de la session HTTP. */
    default String sessionId() {
        return binding().current();
    }

    /** TRN-12, AUTH-23 : la session a changé d'identifiant ; la connexion la suit. */
    void follow(String renamedSessionId);

    /** TRN-12 : le nouvel identifiant est confirmé en base ; sans effet si la liaison a changé depuis {@code seen}. */
    void settle(SessionBinding seen);

    /** TRN-13 : instant de la dernière trame reçue, ou de l'ouverture. */
    Instant lastHeardAt();

    /** Envoie une trame texte ; une connexion déjà fermée l'ignore. */
    void send(String text);

    /** Ferme la connexion avec le code de sa cause. */
    void close(CloseReason reason);
}
