package fr.vlegall.seal.friends.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Objects;

/** FRD-10 : un ami et la date de l'amitié, vus depuis l'autre membre de la paire. */
public record Friend(UserId user, Instant since) {

    public Friend {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(since, "since");
    }
}
