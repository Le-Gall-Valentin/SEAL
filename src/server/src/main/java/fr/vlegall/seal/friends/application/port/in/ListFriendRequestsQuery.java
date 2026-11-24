package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.Invitation;
import java.util.Optional;
import java.util.UUID;

/** Section 14.4, ADR 0037 (point 3) : invitations en attente reçues ou envoyées, paginées. */
public interface ListFriendRequestsQuery {

    Page<Invitation> list(UserId user, Direction direction, Optional<UUID> after, int limit);
}
