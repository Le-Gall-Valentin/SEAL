package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;

/** GRP-03 : un membre quitte un groupe ; sans administrateur, le plus ancien le devient. */
public interface LeaveConversationUseCase {

    void leave(UserId caller, ConversationId id);
}
