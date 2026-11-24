package fr.vlegall.seal.conversations.domain.model;

import java.util.Objects;

/** CNV-01 : la directe de la paire ; {@code created} dit si cette demande l'a créée (201) ou retrouvée (200). */
public record OpenedDirect(boolean created, ConversationSummary conversation) {

    public OpenedDirect {
        Objects.requireNonNull(conversation, "conversation");
    }
}
