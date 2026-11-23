package fr.vlegall.seal.friends.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Direction;
import fr.vlegall.seal.friends.domain.model.FriendRequest;
import fr.vlegall.seal.friends.domain.model.RequestStatus;
import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.FriendRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code friend_requests} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class FriendRequestRepositoryAdapter implements FriendRequestRepository {

    private static final String COLUMNS = "id, sender_id, receiver_id, status, created_at";

    private final JdbcClient jdbc;

    public FriendRequestRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FriendRequest> pendingBetween(UserPair pair) {
        return jdbc.sql(
                        "SELECT " + COLUMNS + " FROM friend_requests WHERE status = 'PENDING'"
                                + " AND ((sender_id = :low AND receiver_id = :high) OR (sender_id = :high AND receiver_id = :low))")
                .param("low", pair.low().value())
                .param("high", pair.high().value())
                .query(FriendRequestRepositoryAdapter::toRequest)
                .optional();
    }

    /**
     * FRD-01 : {@code ON CONFLICT DO NOTHING} plutôt qu'une exception, qui interromprait la transaction PostgreSQL. Deux
     * invitations concurrentes de la même paire : la seconde attend la première, puis n'insère rien.
     */
    @Override
    public Optional<FriendRequest> createPending(UserId sender, UserId receiver, Instant createdAt) {
        return jdbc.sql("INSERT INTO friend_requests (sender_id, receiver_id, status, created_at)"
                        + " VALUES (:sender, :receiver, 'PENDING', :createdAt) ON CONFLICT DO NOTHING RETURNING id")
                .param("sender", sender.value())
                .param("receiver", receiver.value())
                .param("createdAt", Timestamp.from(createdAt))
                .query(UUID.class)
                .optional()
                .map(id -> new FriendRequest(id, sender, receiver, RequestStatus.PENDING, createdAt));
    }

    @Override
    public Optional<FriendRequest> lockPending(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM friend_requests WHERE id = :id AND status = 'PENDING' FOR UPDATE")
                .param("id", id)
                .query(FriendRequestRepositoryAdapter::toRequest)
                .optional();
    }

    @Override
    public void close(UUID id, RequestStatus status, Instant respondedAt) {
        int updated = jdbc.sql("UPDATE friend_requests SET status = :status, responded_at = :at"
                        + " WHERE id = :id AND status = 'PENDING'")
                .param("status", status.name())
                .param("at", Timestamp.from(respondedAt))
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("invitation absente ou déjà close");
        }
    }

    @Override
    public List<FriendRequest> pending(UserId user, Direction direction, Optional<UUID> after, int limit) {
        String column = switch (direction) {
            case INCOMING -> "receiver_id";
            case OUTGOING -> "sender_id";
        };
        String sql = "SELECT " + COLUMNS + " FROM friend_requests WHERE status = 'PENDING' AND " + column + " = :user"
                + (after.isPresent() ? " AND id > :after" : "") + " ORDER BY id LIMIT :limit";
        JdbcClient.StatementSpec statement =
                jdbc.sql(sql).param("user", user.value()).param("limit", limit);
        if (after.isPresent()) {
            statement = statement.param("after", after.get());
        }
        return statement.query(FriendRequestRepositoryAdapter::toRequest).list();
    }

    private static FriendRequest toRequest(ResultSet row, int number) throws SQLException {
        return new FriendRequest(
                row.getObject("id", UUID.class),
                new UserId(row.getObject("sender_id", UUID.class)),
                new UserId(row.getObject("receiver_id", UUID.class)),
                RequestStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant());
    }
}
