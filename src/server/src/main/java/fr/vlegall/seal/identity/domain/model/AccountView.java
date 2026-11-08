package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Compte courant, relu en base à chaque appel de {@code GET /api/v1/auth/me} (ADR 0019, point 5).
 *
 * @param verification données de la vérification pour un compte en attente, {@code null} sinon
 */
public record AccountView(
        UserId id,
        Username username,
        DisplayName displayName,
        EmailAddress email,
        AccountStatus status,
        @Nullable PendingVerification verification) {}
