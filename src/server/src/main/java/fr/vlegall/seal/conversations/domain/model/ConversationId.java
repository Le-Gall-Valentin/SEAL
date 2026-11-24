package fr.vlegall.seal.conversations.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identifiant d'une conversation (UUIDv7, {@code conversations.id}). */
public record ConversationId(UUID value) {

    public ConversationId {
        Objects.requireNonNull(value, "value");
    }
}
