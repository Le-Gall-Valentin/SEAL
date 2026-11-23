package fr.vlegall.seal.friends.infrastructure.messaging;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.InteractionStats;
import fr.vlegall.seal.friends.domain.port.out.InteractionStatsPort;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ADR 0037, point 4 : avant la messagerie du lot 4, aucun message n'existe, donc aucune interaction. Le lot 4 remplace
 * cet adaptateur par un appel au port d'entrée du contexte {@code messaging}.
 */
@Component
public class NoInteractionStatsAdapter implements InteractionStatsPort {

    @Override
    public Map<UserId, InteractionStats> between(UserId user, Collection<UserId> friends) {
        return Map.of();
    }
}
