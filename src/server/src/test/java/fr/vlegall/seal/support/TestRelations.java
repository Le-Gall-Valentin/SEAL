package fr.vlegall.seal.support;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Relations écrites directement en base, sans passer par les routes : amitiés, compte supprimé et conversations, pour
 * les tests qui en ont besoin comme point de départ.
 */
public final class TestRelations {

    private TestRelations() {}

    /** Amitié entre {@code a} et {@code b}, sans invitation. */
    public static void befriend(JdbcClient jdbc, UUID a, UUID b) {
        jdbc.sql("INSERT INTO friendships (user_low, user_high) VALUES (LEAST(:a, :b), GREATEST(:a, :b))")
                .param("a", a)
                .param("b", b)
                .update();
    }

    /** FRD-11 : amitié retirée. */
    public static void unfriend(JdbcClient jdbc, UUID a, UUID b) {
        jdbc.sql("DELETE FROM friendships WHERE user_low = LEAST(:a, :b) AND user_high = GREATEST(:a, :b)")
                .param("a", a)
                .param("b", b)
                .update();
    }

    /** Compte supprimé (ACC-07), donc plus actif. */
    public static void deactivate(JdbcClient jdbc, UUID user) {
        jdbc.sql("UPDATE users SET status = 'DELETED', email = NULL, deleted_at = now() WHERE id = ?")
                .param(user)
                .update();
    }

    /** CNV-01 : conversation directe entre {@code a} et {@code b}. */
    public static UUID direct(JdbcClient jdbc, UUID a, UUID b) {
        UUID id = jdbc.sql("INSERT INTO conversations (type, direct_key, created_by) VALUES ('DIRECT',"
                        + " LEAST(:a, :b)::text || ':' || GREATEST(:a, :b)::text, :a) RETURNING id")
                .param("a", a)
                .param("b", b)
                .query(UUID.class)
                .single();
        join(jdbc, id, a, "MEMBER");
        join(jdbc, id, b, "MEMBER");
        return id;
    }

    /** GRP-01 : groupe « Ski » administré par {@code admin}, avec {@code members}. */
    public static UUID group(JdbcClient jdbc, UUID admin, UUID... members) {
        UUID id = jdbc.sql("INSERT INTO conversations (type, name, created_by) VALUES ('GROUP', 'Ski', ?) RETURNING id")
                .param(admin)
                .query(UUID.class)
                .single();
        join(jdbc, id, admin, "ADMIN");
        for (UUID member : members) {
            join(jdbc, id, member, "MEMBER");
        }
        return id;
    }

    /** GRP-03 : {@code user} quitte {@code conversation}. */
    public static void leave(JdbcClient jdbc, UUID conversation, UUID user) {
        jdbc.sql(
                        "UPDATE conversation_members SET left_at = now(), left_seq = 0 WHERE conversation_id = ? AND user_id = ?")
                .param(conversation)
                .param(user)
                .update();
    }

    private static void join(JdbcClient jdbc, UUID conversation, UUID user, String role) {
        jdbc.sql("INSERT INTO conversation_members (conversation_id, user_id, role, joined_seq) VALUES (?, ?, ?, 0)")
                .param(conversation)
                .param(user)
                .param(role)
                .update();
    }
}
