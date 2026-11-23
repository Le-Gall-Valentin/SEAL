package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Section 10.1 : invitation de {@code sender} à {@code receiver}. */
public record FriendRequest(UUID id, UserId sender, UserId receiver, RequestStatus status, Instant createdAt) {

    public FriendRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sender.equals(receiver)) {
            throw new IllegalArgumentException("une invitation relie deux comptes distincts");
        }
    }

    public boolean sentBy(UserId user) {
        return sender.equals(user);
    }

    public boolean sentTo(UserId user) {
        return receiver.equals(user);
    }

    public UserPair pair() {
        return UserPair.of(sender, receiver);
    }

    /** L'autre compte de l'invitation, vu par l'un des deux. */
    public UserId other(UserId viewer) {
        return pair().other(viewer);
    }
}
