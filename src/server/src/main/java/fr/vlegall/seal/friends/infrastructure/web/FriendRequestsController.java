package fr.vlegall.seal.friends.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.AcceptFriendRequestUseCase;
import fr.vlegall.seal.friends.application.port.in.DeclineFriendRequestUseCase;
import fr.vlegall.seal.friends.application.port.in.ListFriendRequestsQuery;
import fr.vlegall.seal.friends.application.port.in.RevokeFriendRequestUseCase;
import fr.vlegall.seal.friends.application.port.in.SendFriendRequestUseCase;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.InvitationOutcome;
import fr.vlegall.seal.friends.domain.model.Page;
import fr.vlegall.seal.friends.infrastructure.web.dto.InvitationView;
import fr.vlegall.seal.friends.infrastructure.web.dto.PageView;
import fr.vlegall.seal.friends.infrastructure.web.dto.SendFriendRequestBody;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Section 14.4 : invitations, réservées à la session complète d'un compte actif ({@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/friend-requests")
public class FriendRequestsController {

    private final SendFriendRequestUseCase invitations;
    private final AcceptFriendRequestUseCase accepts;
    private final DeclineFriendRequestUseCase declines;
    private final RevokeFriendRequestUseCase revocations;
    private final ListFriendRequestsQuery lists;
    private final RateLimiter limiter;

    public FriendRequestsController(
            SendFriendRequestUseCase invitations,
            AcceptFriendRequestUseCase accepts,
            DeclineFriendRequestUseCase declines,
            RevokeFriendRequestUseCase revocations,
            ListFriendRequestsQuery lists,
            RateLimiter limiter) {
        this.invitations = invitations;
        this.accepts = accepts;
        this.declines = declines;
        this.revocations = revocations;
        this.lists = lists;
        this.limiter = limiter;
    }

    /** Section 14.4, ADR 0037 (point 3) : invitations en attente reçues ({@code incoming}) ou envoyées. */
    @GetMapping
    public PageView<InvitationView> list(
            @CurrentUser UserId user,
            @RequestParam @Pattern(regexp = "incoming|outgoing") String direction,
            @RequestParam Optional<UUID> after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(Page.MAX_LIMIT) int limit) {
        Direction chosen = direction.equals("incoming") ? Direction.INCOMING : Direction.OUTGOING;
        return PageView.of(lists.list(user, chosen, after, limit), InvitationView::of);
    }

    /**
     * FRD-02, 15.2 : chaque tentative prend un jeton des 60, et un des 20 créations, rendu si rien n'est créé (ADR 0037,
     * point 7). Les jetons se prennent avant l'appel, hors transaction.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationView send(@CurrentUser UserId user, @Valid @RequestBody SendFriendRequestBody body) {
        limiter.consume(RateLimit.INVITATION_ATTEMPTS_PER_USER, user);
        limiter.consume(RateLimit.INVITATIONS_CREATED_PER_USER, user);
        InvitationOutcome result = null;
        try {
            result = invitations.send(user, body.username());
        } finally {
            if (!(result instanceof InvitationOutcome.Sent)) {
                limiter.refund(RateLimit.INVITATIONS_CREATED_PER_USER, user);
            }
        }
        return switch (result) {
            case InvitationOutcome.Sent sent -> InvitationView.of(sent.invitation());
            case InvitationOutcome.Malformed malformed ->
                throw new SealException(ErrorCode.VALIDATION_FAILED, "username");
            case InvitationOutcome.UserNotFound unknown -> throw new SealException(ErrorCode.USER_NOT_FOUND);
            case InvitationOutcome.Self self -> throw new SealException(ErrorCode.CANNOT_INVITE_SELF);
            case InvitationOutcome.CannotInvite refused -> throw new SealException(ErrorCode.CANNOT_INVITE);
            case InvitationOutcome.AlreadyFriends friends -> throw new SealException(ErrorCode.ALREADY_FRIENDS);
            case InvitationOutcome.AlreadyPending pending -> throw new SealException(ErrorCode.REQUEST_ALREADY_PENDING);
            case InvitationOutcome.IncomingExists incoming -> throw new IncomingRequestExists(incoming.requestId());
        };
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@CurrentUser UserId user, @PathVariable UUID id) {
        found(accepts.accept(user, id));
    }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@CurrentUser UserId user, @PathVariable UUID id) {
        found(declines.decline(user, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@CurrentUser UserId user, @PathVariable UUID id) {
        found(revocations.revoke(user, id));
    }

    /** A01 : une invitation inconnue, d'un autre ou close reçoit le même 404. */
    private static void found(boolean done) {
        if (!done) {
            throw new SealException(ErrorCode.NOT_FOUND);
        }
    }
}
