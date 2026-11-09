package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/** AUTH-50 : les sessions d'un compte se retrouvent et se révoquent en base. */
@SealIntegrationTest
class SessionRevocationIT {

    @Autowired
    private SessionRevocation revocation;

    @Autowired
    private JdbcIndexedSessionRepository repository;

    @Autowired
    private JdbcClient jdbc;

    /** Compte réel : le journal rattache l'événement à une ligne de {@code users}. */
    private UserId account() {
        String username = "rev" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name) VALUES (?, 'Nom') RETURNING id")
                .param(username)
                .query(UUID.class)
                .single());
    }

    private List<String> revocationEvents(UserId user) {
        return jdbc.sql(
                        "SELECT details->>'sessions' FROM security_events WHERE user_id = ? AND type = 'SESSION_REVOKED'")
                .param(user.value())
                .query(String.class)
                .list();
    }

    /** Session du compte, enregistrée en base ; {@code JdbcSession} n'étant pas public, par l'interface brute. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String sessionOf(UserId user) {
        FindByIndexNameSessionRepository raw = repository;
        Session session = raw.createSession();
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.value().toString());
        raw.save(session);
        return session.getId();
    }

    private int sessionsOf(UserId user) {
        return repository.findByPrincipalName(user.value().toString()).size();
    }

    @Test
    @DisplayName(
            "AUTH-50 : révoquer les autres sessions d'un compte garde la session courante et celles des autres comptes")
    void auth50RevokeOthersKeepsTheCurrentSession() {
        UserId user = account();
        UserId neighbour = account();
        String current = sessionOf(user);
        sessionOf(user);
        sessionOf(user);
        sessionOf(neighbour);

        revocation.revokeOthers(user, current);

        assertThat(repository.findByPrincipalName(user.value().toString())).containsOnlyKeys(current);
        assertThat(sessionsOf(neighbour)).isEqualTo(1);
    }

    @Test
    @DisplayName("AUTH-70 : révoquer toutes les sessions d'un compte n'en laisse aucune, et seulement les siennes")
    void auth70RevokeAllRemovesEverySessionOfTheAccount() {
        UserId user = account();
        UserId neighbour = account();
        sessionOf(user);
        sessionOf(user);
        sessionOf(neighbour);

        revocation.revokeAll(user);

        assertThat(sessionsOf(user)).isZero();
        assertThat(sessionsOf(neighbour)).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-20 : une révocation est journalisée avec le nombre de sessions tombées ; rien si aucune ne tombe")
    void sec20RevocationIsJournaledOnlyWhenSessionsFall() {
        UserId user = account();
        String current = sessionOf(user);
        sessionOf(user);
        sessionOf(user);
        UserId alone = account();
        String only = sessionOf(alone);

        revocation.revokeOthers(user, current);
        revocation.revokeOthers(alone, only);

        assertThat(revocationEvents(user)).containsExactly("2");
        assertThat(revocationEvents(alone)).isEmpty();
    }
}
