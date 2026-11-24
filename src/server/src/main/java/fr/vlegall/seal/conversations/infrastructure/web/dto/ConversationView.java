package fr.vlegall.seal.conversations.infrastructure.web.dto;

import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * CNV-04, ADR 0039 (point 6) : vue d'une conversation. {@code lastEnvelope} reste {@code null} jusqu'à la messagerie du
 * lot 4, qui en fixera la forme ; le client déchiffre l'aperçu, le serveur ne le peut pas.
 */
public record ConversationView(
        UUID id,
        String type,
        @Nullable String name,
        List<MemberView> members,
        Instant createdAt,
        long lastServerSeq,
        long unreadCount,
        @Nullable Long lastSenderSeq,
        @Nullable Object lastEnvelope) {

    public ConversationView {
        members = List.copyOf(members);
    }

    public static ConversationView of(ConversationSummary conversation) {
        return new ConversationView(
                conversation.id().value(),
                conversation.type().name(),
                conversation.name().map(GroupName::value).orElse(null),
                conversation.members().stream().map(MemberView::of).toList(),
                conversation.createdAt(),
                conversation.lastServerSeq(),
                conversation.activity().unreadCount(),
                conversation.activity().lastSenderSeq().isPresent()
                        ? conversation.activity().lastSenderSeq().getAsLong()
                        : null,
                null);
    }
}
