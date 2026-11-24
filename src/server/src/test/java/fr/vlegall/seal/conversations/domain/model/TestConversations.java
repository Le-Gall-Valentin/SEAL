package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Conversations construites pour les tests. En production, la base crée la directe en une instruction
 * ({@code ConversationRepository.createDirect}) puis la relit : le modèle n'a pas besoin de cette fabrique.
 */
public final class TestConversations {

    private TestConversations() {}

    /** CNV-01 : directe entre deux comptes distincts, sans nom ni administrateur, telle que la base la crée. */
    public static Conversation direct(ConversationId id, UserId a, UserId b, Instant at) {
        DirectKey.of(a, b);
        return new Conversation(
                id,
                ConversationType.DIRECT,
                Optional.empty(),
                a,
                at,
                0,
                List.of(new Member(a, Role.MEMBER, at), new Member(b, Role.MEMBER, at)));
    }
}
