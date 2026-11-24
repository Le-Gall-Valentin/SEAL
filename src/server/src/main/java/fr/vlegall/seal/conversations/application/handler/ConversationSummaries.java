package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Activity;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.ConversationType;
import fr.vlegall.seal.conversations.domain.model.Member;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import fr.vlegall.seal.conversations.domain.model.MemberSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationActivityPort;
import fr.vlegall.seal.conversations.domain.port.out.MemberDirectoryPort;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CNV-04, ADR 0039 (point 6) : vue d'une conversation pour un membre : profils des membres actifs aux comptes actifs,
 * lus en une fois, et activité. Partagé par les handlers ; appelé dans leur transaction.
 */
@ApplicationService
public class ConversationSummaries {

    private final MemberDirectoryPort directory;
    private final ConversationActivityPort activity;

    public ConversationSummaries(MemberDirectoryPort directory, ConversationActivityPort activity) {
        this.directory = directory;
        this.activity = activity;
    }

    /** Détail : la conversation telle que {@code viewer} la voit. */
    ConversationSummary of(UserId viewer, Conversation conversation) {
        return summaries(viewer, List.of(conversation), false).getFirst();
    }

    /** Liste : une directe dont l'autre compte n'est plus actif est omise. */
    List<ConversationSummary> listed(UserId viewer, List<Conversation> conversations) {
        return summaries(viewer, conversations, true);
    }

    private List<ConversationSummary> summaries(UserId viewer, List<Conversation> conversations, boolean listing) {
        List<UserId> users = conversations.stream()
                .flatMap(c -> c.activeMembers().stream().map(Member::user))
                .distinct()
                .toList();
        Map<UserId, MemberProfile> profiles = directory.activeProfiles(users).stream()
                .collect(Collectors.toMap(MemberProfile::user, Function.identity()));
        Map<ConversationId, Activity> activities = activity.activityOf(
                viewer, conversations.stream().map(Conversation::id).toList());
        return conversations.stream()
                .filter(c -> !listing || c.type() != ConversationType.DIRECT || counterpartActive(viewer, c, profiles))
                .map(c -> new ConversationSummary(
                        c.id(),
                        c.type(),
                        c.name(),
                        c.activeMembers().stream()
                                .filter(m -> profiles.containsKey(m.user()))
                                .map(m -> new MemberSummary(profiles.get(m.user()), m.role()))
                                .toList(),
                        c.createdAt(),
                        c.lastServerSeq(),
                        activities.getOrDefault(c.id(), Activity.NONE)))
                .toList();
    }

    private static boolean counterpartActive(UserId viewer, Conversation direct, Map<UserId, MemberProfile> profiles) {
        return direct.activeMembers().stream()
                .map(Member::user)
                .filter(user -> !user.equals(viewer))
                .allMatch(profiles::containsKey);
    }
}
