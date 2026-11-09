package fr.vlegall.seal.identity.infrastructure.web.dto;

import fr.vlegall.seal.identity.domain.model.AccountView;
import fr.vlegall.seal.identity.domain.model.PendingVerification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Compte courant, corps de {@code GET /me}, de {@code register} et de {@code verify-email} (ADR 0019, point 5). */
public record MeResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String status,
        List<String> factors,
        @Nullable Verification verification) {

    public MeResponse {
        factors = List.copyOf(factors);
    }

    /** Données de Verification-Courriel ; dates en ISO 8601 UTC. */
    public record Verification(@Nullable Instant codeExpiresAt, long resendsRemaining) {}

    public static MeResponse of(AccountView account, List<String> factors) {
        PendingVerification pending = account.verification();
        return new MeResponse(
                account.id().value(),
                account.username().value(),
                account.displayName().value(),
                account.email().value(),
                account.status().name(),
                List.copyOf(factors),
                pending == null ? null : new Verification(pending.codeExpiresAt(), pending.resendsRemaining()));
    }
}
