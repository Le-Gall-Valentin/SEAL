package fr.vlegall.seal.conversations.domain.model;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Section 11, GRP-01 à GRP-04 : une conversation et ses membres actifs. Immuable : chaque action rend la conversation
 * qui en résulte, que le dépôt enregistre sous le verrou de la conversation (ADR 0039).
 */
public record Conversation(
        ConversationId id,
        ConversationType type,
        Optional<GroupName> name,
        UserId createdBy,
        Instant createdAt,
        long lastServerSeq,
        List<Member> activeMembers) {

    /** GRP-01 : 2 à 50 membres, créateur compris. */
    public static final int MAX_MEMBERS = 50;

    /** GRP-01 : arrivants d'une seule requête au plus, l'appelant étant déjà membre. */
    public static final int MAX_NEWCOMERS = MAX_MEMBERS - 1;

    /** ADR 0039 (point 4) : le plus tôt arrivé, puis le plus petit identifiant. */
    private static final Comparator<Member> SENIORITY =
            Comparator.comparing(Member::joinedAt).thenComparing(Member::user, UserId.POSTGRES_ORDER);

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        activeMembers = List.copyOf(activeMembers);
    }

    /** GRP-01, CNV-03 : groupe, créateur administrateur, 1 à 49 autres membres. */
    public static Conversation group(
            ConversationId id, UserId creator, GroupName name, Set<UserId> others, Instant at) {
        if (others.isEmpty() || others.contains(creator)) {
            throw new IllegalArgumentException("un groupe compte le créateur et au moins un autre membre");
        }
        if (others.size() + 1 > MAX_MEMBERS) {
            throw new ConversationException.GroupFull();
        }
        List<Member> members = new ArrayList<>();
        members.add(new Member(creator, Role.ADMIN, at));
        others.forEach(user -> members.add(new Member(user, Role.MEMBER, at)));
        return new Conversation(id, ConversationType.GROUP, Optional.of(name), creator, at, 0, members);
    }

    /** Les comptes des membres actifs. */
    public Set<UserId> memberIds() {
        return activeMembers.stream().map(Member::user).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isActiveMember(UserId user) {
        return activeMembers.stream().anyMatch(member -> member.user().equals(user));
    }

    public Optional<UserId> admin() {
        return activeMembers.stream()
                .filter(member -> member.role() == Role.ADMIN)
                .map(Member::user)
                .findFirst();
    }

    /** CNV-06 : un non-membre ne doit pas apprendre que la conversation existe. */
    public void ensureMember(UserId user) {
        if (!isActiveMember(user)) {
            throw new ConversationException.NotFound();
        }
    }

    /** GRP-03 : tout membre ajoute des arrivants ; un membre actif est ignoré ; 50 membres actifs au plus. */
    public Conversation join(UserId adder, Set<UserId> newcomers, Instant at) {
        ensureGroup();
        ensureMember(adder);
        List<Member> members = new ArrayList<>(activeMembers);
        newcomers.stream()
                .filter(user -> !isActiveMember(user))
                .forEach(user -> members.add(new Member(user, Role.MEMBER, at)));
        if (members.size() > MAX_MEMBERS) {
            throw new ConversationException.GroupFull();
        }
        return with(name, members);
    }

    /** GRP-03 : seul l'administrateur retire un membre ; lui-même part par {@link #depart}. */
    public Conversation exclude(UserId actor, UserId target) {
        ensureGroup();
        ensureAdmin(actor);
        if (actor.equals(target)) {
            throw new ConversationException.Forbidden();
        }
        if (!isActiveMember(target)) {
            throw new ConversationException.NotAMember();
        }
        return with(name, without(target));
    }

    /** GRP-03 : tout membre peut partir ; sans administrateur, le plus ancien le devient (ADR 0039, point 4). */
    public Conversation depart(UserId member) {
        ensureGroup();
        ensureMember(member);
        List<Member> members = without(member);
        if (members.stream().noneMatch(m -> m.role() == Role.ADMIN) && !members.isEmpty()) {
            Member successor = members.stream().min(SENIORITY).orElseThrow();
            members.replaceAll(m -> m.equals(successor) ? m.asAdmin() : m);
        }
        return with(name, members);
    }

    /** GRP-03 : seul l'administrateur renomme le groupe. */
    public Conversation rename(UserId actor, GroupName newName) {
        ensureGroup();
        ensureAdmin(actor);
        return with(Optional.of(newName), activeMembers);
    }

    /** ADR 0039 (point 3) : ajout, retrait, départ et renommage n'existent que pour un groupe. */
    public void ensureGroup() {
        if (type != ConversationType.GROUP) {
            throw new ConversationException.Forbidden();
        }
    }

    private void ensureAdmin(UserId actor) {
        ensureMember(actor);
        if (!admin().map(actor::equals).orElse(false)) {
            throw new ConversationException.Forbidden();
        }
    }

    private List<Member> without(UserId user) {
        List<Member> members = new ArrayList<>(activeMembers);
        members.removeIf(member -> member.user().equals(user));
        return members;
    }

    private Conversation with(Optional<GroupName> newName, List<Member> members) {
        return new Conversation(id, type, newName, createdBy, createdAt, lastServerSeq, members);
    }
}
