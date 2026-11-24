package fr.vlegall.seal.conversations.domain.model;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * CNV-04 : activité d'une conversation pour un membre, calculée sur les messages (lot 4) ; avant eux, aucun non-lu et
 * aucun message de l'appelant (ADR 0039, point 6).
 */
public record Activity(long unreadCount, OptionalLong lastSenderSeq) {

    /** Aucun message : 0 non-lu, aucun {@code senderSeq}. */
    public static final Activity NONE = new Activity(0, OptionalLong.empty());

    public Activity {
        Objects.requireNonNull(lastSenderSeq, "lastSenderSeq");
    }
}
