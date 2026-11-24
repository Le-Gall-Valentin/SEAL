package fr.vlegall.seal.conversations.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationType;
import fr.vlegall.seal.conversations.domain.model.DirectKey;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.model.Member;
import fr.vlegall.seal.conversations.domain.model.Role;
import fr.vlegall.seal.conversations.domain.port.out.ConversationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code conversations} et {@code conversation_members} par {@link JdbcClient} ; SQL constant et paramétré (A05). */
@Component
public class ConversationRepositoryAdapter implements ConversationRepository {

    private static final String COLUMNS = "c.id, c.type, c.name, c.created_by, c.created_at, c.last_server_seq";

    /** CNV-01 : la directe et ses deux membres en une instruction ; rien si la clé existe déjà (index unique). */
    private static final String CREATE_DIRECT = "WITH created AS (INSERT INTO conversations"
            + " (type, direct_key, created_by, created_at) VALUES ('DIRECT', :key, :a, :at)"
            + " ON CONFLICT (direct_key) DO NOTHING RETURNING id)"
            + " INSERT INTO conversation_members (conversation_id, user_id, role, joined_at, joined_seq)"
            + " SELECT created.id, v.member, 'MEMBER', :at, 0 FROM created"
            + " CROSS JOIN (VALUES (CAST(:a AS uuid)), (CAST(:b AS uuid))) AS v(member)";

    /** CNV-06 : la conversation seulement si {@code :member} en est membre actif. */
    private static final String FOR_MEMBER = "SELECT " + COLUMNS + " FROM conversations c WHERE c.id = :id"
            + " AND EXISTS (SELECT 1 FROM conversation_members m WHERE m.conversation_id = c.id"
            + " AND m.user_id = :member AND m.left_at IS NULL)";

    /** CNV-04 : appartenances actives de {@code :member}, après {@code :after}, triées par identifiant. */
    private static final String PAGE = "SELECT " + COLUMNS + " FROM conversations c"
            + " JOIN conversation_members m ON m.conversation_id = c.id"
            + " WHERE m.user_id = :member AND m.left_at IS NULL"
            + " AND (CAST(:after AS uuid) IS NULL OR c.id > CAST(:after AS uuid)) ORDER BY c.id LIMIT :limit";

    private static final String ACTIVE_MEMBERS = "SELECT conversation_id, user_id, role, joined_at"
            + " FROM conversation_members WHERE conversation_id IN (:ids) AND left_at IS NULL"
            + " ORDER BY joined_at, user_id";

    /** GRP-03 : un ancien membre qui revient retrouve sa ligne, remise à neuf. */
    private static final String JOIN = "INSERT INTO conversation_members"
            + " (conversation_id, user_id, role, joined_at, joined_seq) VALUES (:id, :user, :role, :joinedAt, :seq)"
            + " ON CONFLICT (conversation_id, user_id) DO UPDATE SET role = EXCLUDED.role,"
            + " joined_at = EXCLUDED.joined_at, joined_seq = EXCLUDED.joined_seq, left_at = NULL, left_seq = NULL,"
            + " last_read_seq = EXCLUDED.joined_seq";

    private final JdbcClient jdbc;

    public ConversationRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConversationId nextId() {
        return new ConversationId(jdbc.sql("SELECT uuidv7()").query(UUID.class).single());
    }

    @Override
    public boolean createDirect(DirectKey key, UserId a, UserId b, Instant at) {
        return jdbc.sql(CREATE_DIRECT)
                        .param("key", key.value())
                        .param("a", a.value())
                        .param("b", b.value())
                        .param("at", Timestamp.from(at))
                        .update()
                > 0;
    }

    @Override
    public Optional<Conversation> findDirect(DirectKey key) {
        List<Row> rows = jdbc.sql("SELECT " + COLUMNS + " FROM conversations c WHERE c.direct_key = :key")
                .param("key", key.value())
                .query(ConversationRepositoryAdapter::row)
                .list();
        return withMembers(rows).stream().findFirst();
    }

    @Override
    public void createGroup(Conversation group) {
        jdbc.sql("INSERT INTO conversations (id, type, name, created_by, created_at) VALUES (:id, 'GROUP', :name,"
                        + " :createdBy, :at)")
                .param("id", group.id().value())
                .param("name", group.name().map(GroupName::value).orElseThrow())
                .param("createdBy", group.createdBy().value())
                .param("at", Timestamp.from(group.createdAt()))
                .update();
        group.activeMembers().forEach(member -> join(group.id(), member, group.lastServerSeq()));
    }

    @Override
    public Optional<Conversation> findForMember(ConversationId id, UserId member) {
        return forMember(FOR_MEMBER, id, member);
    }

    @Override
    public Optional<Conversation> lockForMember(ConversationId id, UserId member) {
        return forMember(FOR_MEMBER + " FOR UPDATE OF c", id, member);
    }

    @Override
    public void save(Conversation before, Conversation after, Instant at) {
        if (!before.name().equals(after.name())) {
            jdbc.sql("UPDATE conversations SET name = :name WHERE id = :id")
                    .param("name", after.name().map(GroupName::value).orElse(null), Types.VARCHAR)
                    .param("id", after.id().value())
                    .update();
        }
        Map<UserId, Member> was = byUser(before);
        Map<UserId, Member> now = byUser(after);
        was.keySet().stream().filter(user -> !now.containsKey(user)).forEach(user -> depart(after, user, at));
        now.values().forEach(member -> {
            Member previous = was.get(member.user());
            if (previous == null) {
                join(after.id(), member, after.lastServerSeq());
            } else if (previous.role() != member.role()) {
                jdbc.sql("UPDATE conversation_members SET role = :role WHERE conversation_id = :id AND user_id = :user")
                        .param("role", member.role().name())
                        .param("id", after.id().value())
                        .param("user", member.user().value())
                        .update();
            }
        });
    }

    @Override
    public List<Conversation> pageForMember(UserId member, Optional<ConversationId> after, int limit) {
        List<Row> rows = jdbc.sql(PAGE)
                .param("member", member.value())
                .param("after", after.map(ConversationId::value).orElse(null), Types.OTHER)
                .param("limit", limit)
                .query(ConversationRepositoryAdapter::row)
                .list();
        return withMembers(rows);
    }

    @Override
    public boolean shareActiveGroup(UserId a, UserId b) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM conversation_members x JOIN conversation_members y"
                        + " ON x.conversation_id = y.conversation_id JOIN conversations c ON c.id = x.conversation_id"
                        + " WHERE x.user_id = :a AND y.user_id = :b AND c.type = 'GROUP'"
                        + " AND x.left_at IS NULL AND y.left_at IS NULL)")
                .param("a", a.value())
                .param("b", b.value())
                .query(Boolean.class)
                .single();
    }

    /**
     * CNV-06 : l'appartenance est revérifiée sur les membres relus. Sous READ COMMITTED, le {@code EXISTS} de
     * {@link #FOR_MEMBER} voit l'instantané d'avant l'attente du verrou : un membre exclu pendant qu'il attendait
     * passerait ; la lecture des membres, faite après, voit l'exclusion.
     */
    private Optional<Conversation> forMember(String sql, ConversationId id, UserId member) {
        List<Row> rows = jdbc.sql(sql)
                .param("id", id.value())
                .param("member", member.value())
                .query(ConversationRepositoryAdapter::row)
                .list();
        return withMembers(rows).stream()
                .filter(conversation -> conversation.isActiveMember(member))
                .findFirst();
    }

    private void join(ConversationId id, Member member, long seq) {
        jdbc.sql(JOIN)
                .param("id", id.value())
                .param("user", member.user().value())
                .param("role", member.role().name())
                .param("joinedAt", Timestamp.from(member.joinedAt()))
                .param("seq", seq)
                .update();
    }

    private void depart(Conversation conversation, UserId user, Instant at) {
        jdbc.sql("UPDATE conversation_members SET left_at = :at, left_seq = :seq"
                        + " WHERE conversation_id = :id AND user_id = :user AND left_at IS NULL")
                .param("at", Timestamp.from(at))
                .param("seq", conversation.lastServerSeq())
                .param("id", conversation.id().value())
                .param("user", user.value())
                .update();
    }

    /** Une seule lecture des membres pour toutes les conversations lues (pas de N+1). */
    private List<Conversation> withMembers(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Member>> members = jdbc
                .sql(ACTIVE_MEMBERS)
                .param("ids", rows.stream().map(Row::id).toList())
                .query((rs, number) -> new MemberRow(
                        rs.getObject("conversation_id", UUID.class),
                        new Member(
                                new UserId(rs.getObject("user_id", UUID.class)),
                                Role.valueOf(rs.getString("role")),
                                rs.getTimestamp("joined_at").toInstant())))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        MemberRow::conversation, Collectors.mapping(MemberRow::member, Collectors.toList())));
        return rows.stream()
                .map(row -> new Conversation(
                        new ConversationId(row.id()),
                        row.type(),
                        Optional.ofNullable(row.name()).map(GroupName::new),
                        new UserId(row.createdBy()),
                        row.createdAt(),
                        row.lastServerSeq(),
                        members.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private static Map<UserId, Member> byUser(Conversation conversation) {
        return conversation.activeMembers().stream().collect(Collectors.toMap(Member::user, Function.identity()));
    }

    private static Row row(ResultSet rs, int number) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                ConversationType.valueOf(rs.getString("type")),
                rs.getString("name"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("last_server_seq"));
    }

    /** Membre actif lu avec sa conversation. */
    private record MemberRow(UUID conversation, Member member) {}

    /** Ligne de {@code conversations}, avant la lecture de ses membres. */
    private record Row(
            UUID id, ConversationType type, String name, UUID createdBy, Instant createdAt, long lastServerSeq) {}
}
