package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;

/**
 * API-02, ADR 0039 (point 7) : deux comptes sont-ils membres actifs d'une même conversation ? Demandé par le contexte
 * {@code keys} (ADR 0008, règle 7) ; la signature ne porte que {@link UserId}.
 */
public interface ConversationMembershipQuery {

    boolean shareConversation(UserId a, UserId b);
}
