package fr.vlegall.seal.friends.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.application.port.in.ListFriendsQuery;
import fr.vlegall.seal.friends.application.port.in.RemoveFriendUseCase;
import fr.vlegall.seal.friends.infrastructure.web.dto.FriendView;
import fr.vlegall.seal.friends.infrastructure.web.dto.PageView;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Section 14.4 : amis, réservés à la session complète d'un compte actif ({@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendsController {

    private final ListFriendsQuery friends;
    private final RemoveFriendUseCase removals;

    public FriendsController(ListFriendsQuery friends, RemoveFriendUseCase removals) {
        this.friends = friends;
        this.removals = removals;
    }

    /** FRD-10, ADR 0037 (point 3) : page d'amis après {@code after}, de 1 à 50 (50 par défaut). */
    @GetMapping
    public PageView<FriendView> list(
            @CurrentUser UserId user,
            @RequestParam Optional<UUID> after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(Page.MAX_LIMIT) int limit) {
        return PageView.of(friends.list(user, after.map(UserId::new), limit), FriendView::of);
    }

    /** FRD-11 : 204 pour les deux ; 404 si ce compte n'est pas un ami de l'appelant (A01). */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUser UserId user, @PathVariable UUID userId) {
        if (!removals.unfriend(user, new UserId(userId))) {
            throw new SealException(ErrorCode.NOT_FOUND);
        }
    }
}
