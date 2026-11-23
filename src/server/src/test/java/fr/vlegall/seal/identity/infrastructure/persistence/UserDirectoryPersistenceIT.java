package fr.vlegall.seal.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import fr.vlegall.seal.identity.domain.port.out.UserSettingsRepository;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** FRD-03, FRD-10 : lectures en lot des comptes et politique d'invitation de {@code user_settings}. */
@SealIntegrationTest
class UserDirectoryPersistenceIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserSettingsRepository settings;

    @Autowired
    private JdbcClient jdbc;

    private UserId insert(String username, String status) {
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name, email, status) "
                        + "VALUES (?, 'Nom', CAST(? AS citext), ?) RETURNING id")
                .param(username)
                .param(username + "@exemple.ca")
                .param(status)
                .query(UUID.class)
                .single());
    }

    @Test
    @DisplayName("FRD-10 : les comptes demandés sont lus en une fois, inconnus omis, quel que soit leur statut")
    void frd10AccountsAreReadTogether() {
        UserId active = insert("annuaire.actif", "ACTIVE");
        UserId pending = insert("annuaire.attente", "PENDING_EMAIL");
        UserId unknown = new UserId(UUID.randomUUID());

        List<Account> accounts = users.findAll(List.of(pending, unknown, active));

        assertThat(accounts).extracting(Account::id).containsExactlyInAnyOrder(active, pending);
        assertThat(users.findAll(List.of())).isEmpty();
    }

    @Test
    @DisplayName("FRD-03 : la politique enregistrée est lue ; sans ligne, les invitations sont ouvertes (EVERYONE)")
    void frd03PolicyIsReadWithEveryoneAsDefault() {
        UserId open = insert("annuaire.ouvert", "ACTIVE");
        UserId closed = insert("annuaire.ferme", "ACTIVE");
        jdbc.sql("INSERT INTO user_settings (user_id, invitation_policy) VALUES (?, 'NOBODY')")
                .param(closed.value())
                .update();

        assertThat(settings.invitationPolicy(open)).isEqualTo(InvitationPolicy.EVERYONE);
        assertThat(settings.invitationPolicy(closed)).isEqualTo(InvitationPolicy.NOBODY);
    }
}
