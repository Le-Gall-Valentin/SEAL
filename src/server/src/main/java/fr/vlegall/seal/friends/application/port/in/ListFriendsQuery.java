package fr.vlegall.seal.friends.application.port.in;

import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.FriendSummary;
import java.util.Optional;

/** FRD-10, ADR 0037 (point 3) : amis encore actifs, paginés par identifiant d'ami. */
public interface ListFriendsQuery {

    Page<FriendSummary> list(UserId user, Optional<UserId> after, int limit);
}
