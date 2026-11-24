package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Activity;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import java.util.Collection;
import java.util.Map;

/** CNV-04 : non-lus et dernier {@code senderSeq} de {@code member}, calculés sur les messages (lot 4). */
@FunctionalInterface
public interface ConversationActivityPort {

    /** Une conversation absente de la réponse n'a aucune activité ({@link Activity#NONE}). */
    Map<ConversationId, Activity> activityOf(UserId member, Collection<ConversationId> conversations);
}
