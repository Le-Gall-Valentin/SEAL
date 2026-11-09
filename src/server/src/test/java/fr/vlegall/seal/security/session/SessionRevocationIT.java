package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        UserId user = new UserId(UUID.randomUUID());
        UserId neighbour = new UserId(UUID.randomUUID());
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
        UserId user = new UserId(UUID.randomUUID());
        UserId neighbour = new UserId(UUID.randomUUID());
        sessionOf(user);
        sessionOf(user);
        sessionOf(neighbour);

        revocation.revokeAll(user);

        assertThat(sessionsOf(user)).isZero();
        assertThat(sessionsOf(neighbour)).isEqualTo(1);
    }
}
