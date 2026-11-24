package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;

/**
 * API-02, ADR 0039 (point 7) : deux comptes sont-ils membres actifs d'un même groupe ? Une conversation directe ne
 * compte pas : elle repose sur l'amitié. Demandé par le contexte {@code keys} (ADR 0008, règle 7) ; la signature ne
 * porte que {@link UserId}.
 */
public interface ConversationMembershipQuery {

    boolean shareGroup(UserId a, UserId b);
}
