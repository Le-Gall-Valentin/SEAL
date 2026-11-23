package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Table {@code friend_requests}. */
public interface FriendRequestRepository {

    /** FRD-01 : l'invitation en attente de la paire, quel que soit son sens. */
    Optional<FriendRequest> pendingBetween(UserPair pair);

    /**
     * FRD-01 : crée l'invitation en attente ; vide si la paire en a déjà une, dans un sens ou l'autre (index
     * {@code one_pending_request_per_pair}), sans interrompre la transaction en cours.
     */
    Optional<FriendRequest> createPending(UserId sender, UserId receiver, Instant createdAt);

    /**
     * Section 10.1 : l'invitation si elle est encore en attente, verrouillée jusqu'à la fin de la transaction ; une
     * transition concurrente attend, puis la trouve close.
     */
    Optional<FriendRequest> lockPending(UUID id);

    /**
     * Section 10.1 : clôt une invitation en attente.
     *
     * @throws IllegalStateException si elle n'est plus en attente (échec fermé)
     */
    void close(UUID id, RequestStatus status, Instant respondedAt);

    /** Section 14.4 : invitations en attente reçues ou envoyées, après {@code after}, triées par identifiant. */
    List<FriendRequest> pending(UserId user, Direction direction, Optional<UUID> after, int limit);
}
