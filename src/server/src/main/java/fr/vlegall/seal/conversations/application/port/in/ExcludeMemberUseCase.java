package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;

/** GRP-03 : l'administrateur retire un membre. */
public interface ExcludeMemberUseCase {

    void exclude(UserId caller, ConversationId id, UserId target);
}
