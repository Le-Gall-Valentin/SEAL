package fr.vlegall.seal.conversations.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CNV-04, ADR 0039 (point 6) : une conversation telle que son membre la voit : membres actifs aux comptes actifs, numéro
 * du dernier message et activité (non-lus, dernier {@code senderSeq}).
 */
public record ConversationSummary(
        ConversationId id,
        ConversationType type,
        Optional<GroupName> name,
        List<MemberSummary> members,
        Instant createdAt,
        long lastServerSeq,
        Activity activity) {

    public ConversationSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        members = List.copyOf(members);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(activity, "activity");
    }
}
