package fr.vlegall.seal.identity.domain.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Données de Verification-Courriel (ADR 0019, point 5) : « Expire dans » et « N renvois restants cette heure ».
 *
 * @param codeExpiresAt expiration du dernier code non consommé ; {@code null} s'il n'y en a aucun
 */
public record PendingVerification(@Nullable Instant codeExpiresAt, long resendsRemaining) {}
