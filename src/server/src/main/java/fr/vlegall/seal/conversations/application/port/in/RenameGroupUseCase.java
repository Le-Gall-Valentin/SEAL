package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;

/** GRP-03 : l'administrateur renomme le groupe. */
public interface RenameGroupUseCase {

    ConversationSummary rename(UserId caller, ConversationId id, String name);
}
