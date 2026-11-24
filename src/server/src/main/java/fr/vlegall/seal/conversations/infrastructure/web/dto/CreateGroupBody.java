package fr.vlegall.seal.conversations.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/conversations/group} (CNV-03) : nom (1 à 64 points de code, vérifiés par le domaine ;
 * 128 unités UTF-16 au plus, SEC-01) et 1 à 49 amis du créateur.
 */
public record CreateGroupBody(
        @NotNull @Size(min = 1, max = 128) String name,
        @NotNull @Size(min = 1, max = 49) List<@NotNull UUID> memberIds) {

    public CreateGroupBody {
        memberIds = List.copyOf(memberIds);
    }
}
