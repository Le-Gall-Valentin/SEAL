package fr.vlegall.seal.conversations.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import java.util.Set;

/** CNV-03, GRP-01, GRP-02, FRD-21 : créer un groupe avec ses membres initiaux, tous amis du créateur. */
public interface CreateGroupUseCase {

    ConversationSummary create(UserId creator, String name, Set<UserId> memberIds);
}
