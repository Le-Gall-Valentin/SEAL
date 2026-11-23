package fr.vlegall.seal.friends.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Friend;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.FriendshipRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code friendships} par {@link JdbcClient} : une ligne par paire, {@code user_low < user_high}. */
@Component
public class FriendshipRepositoryAdapter implements FriendshipRepository {

    /** FRD-10 : amis de {@code :user}, après {@code :after} s'il est donné, triés par identifiant ; constante (A05). */
    private static final String FRIENDS_OF = "SELECT friend, created_at FROM (SELECT CASE WHEN user_low = :user"
            + " THEN user_high ELSE user_low END AS friend, created_at FROM friendships"
            + " WHERE user_low = :user OR user_high = :user) AS mine"
            + " WHERE CAST(:after AS uuid) IS NULL OR friend > CAST(:after AS uuid) ORDER BY friend LIMIT :limit";

    private final JdbcClient jdbc;

    public FriendshipRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UserPair pair) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM friendships WHERE user_low = :low AND user_high = :high)")
                .param("low", pair.low().value())
                .param("high", pair.high().value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean create(UserPair pair, Instant since) {
        return jdbc.sql("INSERT INTO friendships (user_low, user_high, created_at) VALUES (:low, :high, :since)"
                                + " ON CONFLICT DO NOTHING")
                        .param("low", pair.low().value())
                        .param("high", pair.high().value())
                        .param("since", Timestamp.from(since))
                        .update()
                == 1;
    }

    @Override
    public boolean dissolve(UserPair pair) {
        return jdbc.sql("DELETE FROM friendships WHERE user_low = :low AND user_high = :high")
                        .param("low", pair.low().value())
                        .param("high", pair.high().value())
                        .update()
                == 1;
    }

    @Override
    public List<Friend> of(UserId user, Optional<UserId> after, int limit) {
        return jdbc.sql(FRIENDS_OF)
                .param("user", user.value())
                .param("after", after.map(UserId::value).orElse(null), Types.OTHER)
                .param("limit", limit)
                .query((row, number) -> new Friend(
                        new UserId(row.getObject("friend", UUID.class)),
                        row.getTimestamp("created_at").toInstant()))
                .list();
    }
}
