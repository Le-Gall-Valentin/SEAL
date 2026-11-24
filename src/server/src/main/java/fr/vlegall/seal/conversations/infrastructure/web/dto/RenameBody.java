package fr.vlegall.seal.conversations.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de {@code PATCH /api/v1/conversations/{id}} (GRP-03) : le nouveau nom, vérifié par le domaine. */
public record RenameBody(@NotNull @Size(min = 1, max = 128) String name) {}
