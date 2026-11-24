package fr.vlegall.seal.keys.infrastructure.conversations;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.application.port.in.ConversationMembershipQuery;
import fr.vlegall.seal.keys.domain.port.out.RelationPort;
import org.springframework.stereotype.Component;

/**
 * API-02, ADR 0039 (point 7) : demande le groupe commun au contexte {@code conversations}, par son port d'entrée (ADR
 * 0008, règle 7) ; seuls les membres actifs d'un groupe comptent, une directe reposant sur l'amitié.
 */
@Component
public class ConversationRelationAdapter implements RelationPort {

    private final ConversationMembershipQuery memberships;

    public ConversationRelationAdapter(ConversationMembershipQuery memberships) {
        this.memberships = memberships;
    }

    @Override
    public boolean related(UserId caller, UserId owner) {
        return memberships.shareGroup(caller, owner);
    }
}
