package fr.vlegall.seal.friends.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.SendFriendRequestUseCase;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.Invitation;
import fr.vlegall.seal.friends.domain.model.InvitationOutcome;
import fr.vlegall.seal.friends.domain.model.InvitedUsername;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.BlockRepository;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class SendFriendRequestHandler implements SendFriendRequestUseCase {

    private final DirectoryPort directory;
    private final BlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final FriendsEventPort events;
    private final Clock clock;

    public SendFriendRequestHandler(
            DirectoryPort directory,
            BlockRepository blocks,
            FriendshipRepository friendships,
            FriendRequestRepository requests,
            FriendsEventPort events,
            Clock clock) {
        this.directory = directory;
        this.blocks = blocks;
        this.friendships = friendships;
        this.requests = requests;
        this.events = events;
        this.clock = clock;
    }

    /** Contrôles dans l'ordre de l'ADR 0037 (point 7), chacun avant toute écriture. */
    @Override
    @Transactional
    public InvitationOutcome send(UserId sender, String input) {
        Optional<String> username = InvitedUsername.normalize(input);
        if (username.isEmpty()) {
            return new InvitationOutcome.Malformed();
        }
        Optional<Person> found = directory.findActiveByUsername(username.get());
        if (found.isEmpty()) {
            return new InvitationOutcome.UserNotFound();
        }
        Person receiver = found.get();
        if (receiver.user().equals(sender)) {
            return new InvitationOutcome.Self();
        }
        UserPair pair = UserPair.of(sender, receiver.user());
        // FRD-01 : une réponse concurrente à une invitation de la paire attend la fin de cet envoi, et inversement.
        requests.lockPair(pair);
        // FRD-03 : une seule issue pour le blocage, dans un sens ou l'autre, et les invitations fermées ; les deux sont
        // toujours consultés, pour qu'aucune durée ne trahisse la cause.
        boolean blocked = blocks.blockedEitherWay(pair);
        boolean open = directory.acceptsInvitations(receiver.user());
        if (blocked || !open) {
            return new InvitationOutcome.CannotInvite();
        }
        if (friendships.exists(pair)) {
            return new InvitationOutcome.AlreadyFriends();
        }
        Optional<FriendRequest> pending = requests.pendingBetween(pair);
        if (pending.isPresent()) {
            return existing(pending.get(), sender);
        }
        Optional<FriendRequest> created = requests.createPending(sender, receiver.user(), clock.instant());
        if (created.isEmpty()) {
            // FRD-01 : une invitation concurrente de la même paire vient d'être validée ; on la relit.
            return requests.pendingBetween(pair)
                    .map(request -> existing(request, sender))
                    .orElseGet(InvitationOutcome.AlreadyPending::new);
        }
        FriendRequest request = created.get();
        events.requestSent(sender, receiver.user(), request.id());
        return new InvitationOutcome.Sent(new Invitation(request.id(), receiver, request.createdAt()));
    }

    private static InvitationOutcome existing(FriendRequest request, UserId sender) {
        return request.sentBy(sender)
                ? new InvitationOutcome.AlreadyPending()
                : new InvitationOutcome.IncomingExists(request.id());
    }
}
