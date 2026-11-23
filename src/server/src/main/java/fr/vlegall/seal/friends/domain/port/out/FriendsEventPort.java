package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;

/** FRD-02, SEC-20, ADR 0037 (point 5) : journal des amis, écrit dans la transaction du geste. */
public interface FriendsEventPort {

    void requestSent(UserId sender, UserId receiver, UUID requestId);

    void requestAccepted(UserId receiver, UserId sender, UUID requestId);

    void friendRemoved(UserId user, UserId formerFriend);
}
