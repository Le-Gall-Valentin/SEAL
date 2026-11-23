package fr.vlegall.seal.friends.infrastructure.persistence.adapter;

import fr.vlegall.seal.friends.domain.model.UserPair;
import fr.vlegall.seal.friends.domain.port.out.BlockRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code blocks} par {@link JdbcClient}. */
@Component
public class BlockRepositoryAdapter implements BlockRepository {

    private final JdbcClient jdbc;

    public BlockRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean blockedEitherWay(UserPair pair) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM blocks WHERE (blocker_id = :low AND blocked_id = :high)"
                        + " OR (blocker_id = :high AND blocked_id = :low))")
                .param("low", pair.low().value())
                .param("high", pair.high().value())
                .query(Boolean.class)
                .single();
    }
}
