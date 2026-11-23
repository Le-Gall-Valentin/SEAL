package fr.vlegall.seal.friends.domain.model;

import java.time.Instant;
import java.util.Objects;

/** FRD-10 : un ami, la date de l'amitié et les statistiques d'interaction. */
public record FriendSummary(Person person, Instant since, InteractionStats stats) {

    public FriendSummary {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(stats, "stats");
    }
}
