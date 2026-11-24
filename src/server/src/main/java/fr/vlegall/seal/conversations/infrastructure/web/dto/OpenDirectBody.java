package fr.vlegall.seal.conversations.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Corps de {@code POST /api/v1/conversations/direct} (CNV-01) : l'ami avec qui ouvrir la directe. */
public record OpenDirectBody(@NotNull UUID userId) {}
