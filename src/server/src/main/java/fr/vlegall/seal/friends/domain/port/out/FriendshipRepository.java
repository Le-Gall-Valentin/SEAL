package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.UserPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Table {@code friendships}. */
public interface FriendshipRepository {

    boolean exists(UserPair pair);

    /** Crée l'amitié ; faux si elle existe déjà. */
    boolean create(UserPair pair, Instant since);

    /** FRD-11 : retire l'amitié pour les deux ; faux s'il n'y en avait pas. */
    boolean dissolve(UserPair pair);

    /** FRD-10 : amis de {@code user} après {@code after}, triés par identifiant d'ami. */
    List<Friend> of(UserId user, Optional<UserId> after, int limit);
}
