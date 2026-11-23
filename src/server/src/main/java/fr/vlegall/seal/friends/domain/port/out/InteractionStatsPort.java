package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.InteractionStats;
import java.util.Collection;
import java.util.Map;

/** FRD-10 : statistiques d'interaction, calculées sur les messages directs (lot 4, ADR 0037, point 4). */
@FunctionalInterface
public interface InteractionStatsPort {

    /** Statistiques de {@code user} avec chacun de ces amis ; un ami absent de la réponse n'a aucune interaction. */
    Map<UserId, InteractionStats> between(UserId user, Collection<UserId> friends);
}
