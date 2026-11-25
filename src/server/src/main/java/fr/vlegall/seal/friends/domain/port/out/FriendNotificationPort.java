package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;

/** FRD-04, ADR 0043 : annonces en direct des invitations et des amitiés, distinctes du journal (FriendsEventPort). */
public interface FriendNotificationPort {

    /** FRD-04 : {@code friend.request} au seul destinataire. */
    void requestReceived(UserId receiver, UUID requestId);

    /** {@code friend.update} aux deux comptes : acceptation, refus, révocation ou retrait. */
    void relationChanged(UserId one, UserId other);
}
