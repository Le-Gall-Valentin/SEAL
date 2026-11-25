package fr.vlegall.seal.ws.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Optional;

/** Une connexion WebSocket ouverte d'un compte, liée à sa session HTTP (TRN-12). */
public interface Connection {

    /** Identifiant propre à la connexion. */
    String id();

    /** Le compte de la session. */
    UserId user();

    /** L'identifiant de la session HTTP à laquelle la connexion est liée. */
    String sessionId();

    /**
     * L'identifiant d'avant le dernier renouvellement de la session (AUTH-23) : la session le garde tant que la requête
     * qui l'a renouvelée n'est pas finie.
     */
    Optional<String> formerSessionId();

    /** TRN-12, AUTH-23 : la session a changé d'identifiant pour le même compte ; la connexion la suit. */
    void follow(String renamedSessionId);

    /** TRN-13 : instant de la dernière trame reçue, ou de l'ouverture. */
    Instant lastHeardAt();

    /** Envoie une trame texte ; une connexion déjà fermée l'ignore. */
    void send(String text);

    /** Ferme la connexion avec le code de sa cause. */
    void close(CloseReason reason);
}
