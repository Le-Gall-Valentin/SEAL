package fr.vlegall.seal.conversations.infrastructure.web.dto;

import fr.vlegall.seal.conversations.domain.model.OpenedDirect;

/** CNV-01, ADR 0039 (point 6) : {@code {"created", "conversation"}}, en 201 à la création, en 200 sinon. */
public record OpenDirectView(boolean created, ConversationView conversation) {

    public static OpenDirectView of(OpenedDirect opened) {
        return new OpenDirectView(opened.created(), ConversationView.of(opened.conversation()));
    }
}
