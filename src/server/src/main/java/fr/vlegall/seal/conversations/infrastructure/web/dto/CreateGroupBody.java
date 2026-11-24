package fr.vlegall.seal.conversations.infrastructure.web.dto;

import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/conversations/group} (CNV-03) : nom (1 à 64 points de code, vérifiés par le domaine ;
 * {@link GroupName#RAW_MAX_LENGTH} unités UTF-16 au plus, SEC-01) et 1 à {@link Conversation#MAX_NEWCOMERS} amis du
 * créateur ; une liste nulle ou un élément nul est refusé avec son champ.
 */
public record CreateGroupBody(
        @NotNull @Size(min = 1, max = GroupName.RAW_MAX_LENGTH)
        String name,

        @NotNull @Size(min = 1, max = Conversation.MAX_NEWCOMERS)
        List<@NotNull UUID> memberIds) {

    public CreateGroupBody {
        // Copie défensive qui garde null et les éléments nuls : Bean Validation les refuse ensuite avec leur champ.
        memberIds = memberIds == null ? null : Collections.unmodifiableList(new ArrayList<>(memberIds));
    }
}
