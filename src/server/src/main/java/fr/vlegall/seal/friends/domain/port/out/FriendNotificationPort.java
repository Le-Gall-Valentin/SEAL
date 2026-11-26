package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;

/** FRD-04, ADR 0043 : annonces en direct des invitations et des amitiés, distinctes du journal (FriendsEventPort). */
public interface FriendNotificationPort {

    /** FRD-04 : {@code friend.request} au seul destinataire. */
    void requestReceived(UserId receiver, UUID requestId);

    /**
     * CDC 10.1, ADR 0044 (décision 8) : {@code friend.update} aux seuls appareils de qui a refusé, « sans notification
     * de refus » pour l'émetteur.
     */
    void requestDeclined(UserId receiver, UserId sender);

    /** {@code friend.update} aux deux comptes : acceptation, révocation ou retrait. */
    void relationChanged(UserId one, UserId other);
}
