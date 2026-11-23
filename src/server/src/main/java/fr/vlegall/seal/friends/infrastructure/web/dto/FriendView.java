package fr.vlegall.seal.friends.infrastructure.web.dto;

import fr.vlegall.seal.friends.domain.model.FriendSummary;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** FRD-10 : un ami servi par {@code GET /api/v1/friends}, dates en ISO 8601 UTC ; {@code null} sans interaction. */
public record FriendView(
        UUID userId,
        String username,
        String displayName,
        Instant friendsSince,
        @Nullable Instant lastInteractionAt,
        int interactionCount30d) {

    public static FriendView of(FriendSummary friend) {
        return new FriendView(
                friend.person().user().value(),
                friend.person().username(),
                friend.person().displayName(),
                friend.since(),
                friend.stats().lastInteractionAt().orElse(null),
                friend.stats().count30d());
    }
}
