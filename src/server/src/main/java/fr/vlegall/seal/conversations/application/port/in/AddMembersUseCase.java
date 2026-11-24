package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import java.util.Set;

/** GRP-03, FRD-21 : tout membre ajoute ses propres amis ; 50 membres actifs au plus. */
public interface AddMembersUseCase {

    ConversationSummary add(UserId caller, ConversationId id, Set<UserId> newcomers);
}
