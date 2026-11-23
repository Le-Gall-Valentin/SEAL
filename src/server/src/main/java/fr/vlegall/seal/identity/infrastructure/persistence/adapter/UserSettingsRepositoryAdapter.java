package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;
import fr.vlegall.seal.identity.domain.port.out.UserSettingsRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code user_settings} : un compte n'y a de ligne qu'une fois un réglage enregistré (ACC-05, ACC-06). */
@Component
public class UserSettingsRepositoryAdapter implements UserSettingsRepository {

    private final JdbcClient jdbc;

    public UserSettingsRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InvitationPolicy invitationPolicy(UserId user) {
        return jdbc.sql("SELECT invitation_policy FROM user_settings WHERE user_id = :user")
                .param("user", user.value())
                .query(String.class)
                .optional()
                .map(InvitationPolicy::valueOf)
                .orElse(InvitationPolicy.EVERYONE);
    }
}
