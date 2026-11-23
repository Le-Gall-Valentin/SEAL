package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Section 10.1, SEC-03 : invitation en attente, verrouillée, si l'acteur y a droit et si l'autre compte est encore
 * actif ; vide sinon, sans dire pourquoi (A01, ADR 0037, point 7). Partagé par l'acceptation, le refus et la
 * révocation ; appelé dans leur transaction.
 */
@ApplicationService
public class OpenRequests {

    private final FriendRequestRepository requests;
    private final DirectoryPort directory;

    public OpenRequests(FriendRequestRepository requests, DirectoryPort directory) {
        this.requests = requests;
        this.directory = directory;
    }

    /** Pour le destinataire seulement : accepter ou refuser. */
    Optional<FriendRequest> lockAddressedTo(UserId receiver, UUID id) {
        return lock(receiver, id, request -> request.sentTo(receiver));
    }

    /** Pour l'émetteur seulement : révoquer. */
    Optional<FriendRequest> lockSentBy(UserId sender, UUID id) {
        return lock(sender, id, request -> request.sentBy(sender));
    }

    private Optional<FriendRequest> lock(UserId actor, UUID id, Predicate<FriendRequest> allowed) {
        return requests.lockPending(id)
                .filter(allowed)
                .filter(request ->
                        !directory.activeProfiles(List.of(request.other(actor))).isEmpty());
    }
}
