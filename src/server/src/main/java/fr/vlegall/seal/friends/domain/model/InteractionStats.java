package fr.vlegall.seal.friends.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** FRD-10, section 10.3 : dernière interaction directe et nombre de messages directs échangés sur 30 jours. */
public record InteractionStats(Optional<Instant> lastInteractionAt, int count30d) {

    /** Aucun message échangé. */
    public static final InteractionStats NONE = new InteractionStats(Optional.empty(), 0);

    public InteractionStats {
        Objects.requireNonNull(lastInteractionAt, "lastInteractionAt");
        if (count30d < 0) {
            throw new IllegalArgumentException("un nombre de messages n'est pas négatif");
        }
    }
}
