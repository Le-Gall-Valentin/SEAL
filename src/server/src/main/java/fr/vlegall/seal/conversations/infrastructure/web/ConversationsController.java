package fr.vlegall.seal.conversations.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.Page;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.PageView;
import fr.vlegall.seal.conversations.application.port.in.AddMembersUseCase;
import fr.vlegall.seal.conversations.application.port.in.CreateGroupUseCase;
import fr.vlegall.seal.conversations.application.port.in.ExcludeMemberUseCase;
import fr.vlegall.seal.conversations.application.port.in.GetConversationQuery;
import fr.vlegall.seal.conversations.application.port.in.LeaveConversationUseCase;
import fr.vlegall.seal.conversations.application.port.in.ListConversationsQuery;
import fr.vlegall.seal.conversations.application.port.in.OpenDirectConversationUseCase;
import fr.vlegall.seal.conversations.application.port.in.RenameGroupUseCase;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.OpenedDirect;
import fr.vlegall.seal.conversations.infrastructure.web.dto.AddMembersBody;
import fr.vlegall.seal.conversations.infrastructure.web.dto.ConversationView;
import fr.vlegall.seal.conversations.infrastructure.web.dto.CreateGroupBody;
import fr.vlegall.seal.conversations.infrastructure.web.dto.OpenDirectBody;
import fr.vlegall.seal.conversations.infrastructure.web.dto.OpenDirectView;
import fr.vlegall.seal.conversations.infrastructure.web.dto.RenameBody;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Section 14.5 : conversations, réservées à la session complète d'un compte actif ({@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationsController {

    private final ListConversationsQuery list;
    private final GetConversationQuery details;
    private final OpenDirectConversationUseCase directs;
    private final CreateGroupUseCase groups;
    private final AddMembersUseCase additions;
    private final ExcludeMemberUseCase exclusions;
    private final LeaveConversationUseCase departures;
    private final RenameGroupUseCase renamings;

    public ConversationsController(
            ListConversationsQuery list,
            GetConversationQuery details,
            OpenDirectConversationUseCase directs,
            CreateGroupUseCase groups,
            AddMembersUseCase additions,
            ExcludeMemberUseCase exclusions,
            LeaveConversationUseCase departures,
            RenameGroupUseCase renamings) {
        this.list = list;
        this.details = details;
        this.directs = directs;
        this.groups = groups;
        this.additions = additions;
        this.exclusions = exclusions;
        this.departures = departures;
        this.renamings = renamings;
    }

    /** CNV-04, ADR 0039 (point 6) : page après {@code after}, de 1 à 50 (50 par défaut). */
    @GetMapping
    public PageView<ConversationView> list(
            @CurrentUser UserId user,
            @RequestParam Optional<UUID> after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(Page.MAX_LIMIT) int limit) {
        return PageView.of(list.list(user, after.map(ConversationId::new), limit), ConversationView::of);
    }

    /** CNV-01 : 201 et {@code created: true} à la création, 200 et {@code false} si elle existait. */
    @PostMapping("/direct")
    public ResponseEntity<OpenDirectView> direct(@CurrentUser UserId user, @Valid @RequestBody OpenDirectBody body) {
        OpenedDirect opened = directs.open(user, new UserId(body.userId()));
        return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(OpenDirectView.of(opened));
    }

    /** CNV-03, GRP-02 : 201 et la vue du groupe. */
    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView group(@CurrentUser UserId user, @Valid @RequestBody CreateGroupBody body) {
        return ConversationView.of(groups.create(user, body.name(), distinct(body.memberIds(), "memberIds")));
    }

    /** CNV-06 : 404 pour un non-membre. */
    @GetMapping("/{id}")
    public ConversationView get(@CurrentUser UserId user, @PathVariable UUID id) {
        return ConversationView.of(details.get(user, new ConversationId(id)));
    }

    /** GRP-03 : 200 et la vue du groupe ; un membre déjà là est ignoré. */
    @PostMapping("/{id}/members")
    public ConversationView add(
            @CurrentUser UserId user, @PathVariable UUID id, @Valid @RequestBody AddMembersBody body) {
        return ConversationView.of(additions.admit(user, new ConversationId(id), distinct(body.userIds(), "userIds")));
    }

    /** GRP-03 : 204 ; réservé à l'administrateur. */
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exclude(@CurrentUser UserId user, @PathVariable UUID id, @PathVariable UUID userId) {
        exclusions.exclude(user, new ConversationId(id), new UserId(userId));
    }

    /** GRP-03 : 204 ; sans administrateur, le plus ancien le devient. */
    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@CurrentUser UserId user, @PathVariable UUID id) {
        departures.leave(user, new ConversationId(id));
    }

    /** GRP-03 : 200 et la vue du groupe ; réservé à l'administrateur. */
    @PatchMapping("/{id}")
    public ConversationView rename(
            @CurrentUser UserId user, @PathVariable UUID id, @Valid @RequestBody RenameBody body) {
        return ConversationView.of(renamings.rename(user, new ConversationId(id), body.name()));
    }

    /** ADR 0039 (point 2) : une liste avec un doublon est refusée en 400, sans écriture. */
    private static Set<UserId> distinct(List<UUID> ids, String field) {
        Set<UserId> users = ids.stream().map(UserId::new).collect(Collectors.toSet());
        if (users.size() != ids.size()) {
            throw new SealException(ErrorCode.VALIDATION_FAILED, field);
        }
        return users;
    }
}
