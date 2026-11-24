package fr.vlegall.seal.conversations.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Corps de {@code POST /api/v1/conversations/{id}/members} (GRP-03) : 1 à 49 amis de l'appelant. */
public record AddMembersBody(
        @NotNull @Size(min = 1, max = 49) List<@NotNull UUID> userIds) {}
