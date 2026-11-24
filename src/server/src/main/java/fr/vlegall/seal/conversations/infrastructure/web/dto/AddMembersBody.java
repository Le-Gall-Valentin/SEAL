package fr.vlegall.seal.conversations.infrastructure.web.dto;

import fr.vlegall.seal.conversations.domain.model.Conversation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/conversations/{id}/members} (GRP-03) : 1 à {@link Conversation#MAX_NEWCOMERS} amis de
 * l'appelant ; une liste nulle ou un élément nul est refusé avec son champ.
 */
public record AddMembersBody(
        @NotNull @Size(min = 1, max = Conversation.MAX_NEWCOMERS)
        List<@NotNull UUID> userIds) {

    public AddMembersBody {
        // Copie défensive qui garde null et les éléments nuls : Bean Validation les refuse ensuite avec leur champ.
        userIds = userIds == null ? null : Collections.unmodifiableList(new ArrayList<>(userIds));
    }
}
