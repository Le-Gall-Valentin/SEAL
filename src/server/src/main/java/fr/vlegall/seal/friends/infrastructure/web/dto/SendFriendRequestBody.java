package fr.vlegall.seal.friends.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/v1/friend-requests} (section 10.1) : la saisie brute, normalisée par le serveur (ADR 0037,
 * point 6), bornée à 64 caractères (SEC-01). Pas d'identifiant de compte : l'émetteur est celui de la session.
 */
public record SendFriendRequestBody(@NotNull @Size(max = 64) String username) {}
