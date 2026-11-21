package fr.vlegall.seal.authentication.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthException;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** AUTH-31 : un compte est lié au couple (fournisseur, identifiant stable), jamais à un courriel. */
@SealIntegrationTest
class OAuthIdentityRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    @Autowired
    private OAuthIdentityRepository identities;

    @Autowired
    private JdbcClient jdbc;

    private UserId insertUser() {
        String username = "oi" + UUID.randomUUID().toString().substring(0, 8);
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name) VALUES (?, 'Nom') RETURNING id")
                .param(username)
                .query(UUID.class)
                .single());
    }

    private static String subject() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static ExternalIdentity identity(OAuthProvider provider, String subject, String email) {
        return ExternalIdentity.of(provider, subject, email, true, "Nom");
    }

    @Test
    @DisplayName("AUTH-31 : le compte lié se retrouve par le fournisseur et le sujet ; le courriel vu est gardé")
    void auth31FoundByProviderAndSubject() {
        UserId user = insertUser();
        String subject = subject();

        identities.link(user, identity(OAuthProvider.GOOGLE, subject, "lie@exemple.ca"), NOW);

        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject)).contains(user);
        assertThat(identities.findUser(OAuthProvider.GITHUB, subject)).isEmpty();
        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject + "0")).isEmpty();
        assertThat(jdbc.sql("SELECT email_seen FROM oauth_identities WHERE user_id = ?")
                        .param(user.value())
                        .query(String.class)
                        .single())
                .isEqualTo("lie@exemple.ca");
        assertThat(jdbc.sql("SELECT linked_at FROM oauth_identities WHERE user_id = ?")
                        .param(user.value())
                        .query(OffsetDateTime.class)
                        .single()
                        .toInstant())
                .isEqualTo(NOW);
    }

    @Test
    @DisplayName("AUTH-31 : un sujet déjà lié à un compte ne se lie pas à un autre (OAUTH_ALREADY_LINKED)")
    void auth31SubjectLinkedOnce() {
        UserId first = insertUser();
        UserId second = insertUser();
        String subject = subject();
        identities.link(first, identity(OAuthProvider.GITHUB, subject, "un@exemple.ca"), NOW);

        assertThatThrownBy(
                        () -> identities.link(second, identity(OAuthProvider.GITHUB, subject, "deux@exemple.ca"), NOW))
                .isInstanceOfSatisfying(
                        OAuthException.AlreadyLinked.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.OAUTH_ALREADY_LINKED));
        assertThat(identities.findUser(OAuthProvider.GITHUB, subject)).contains(first);
    }

    @Test
    @DisplayName("AUTH-31 : un compte a une identité par fournisseur, et peut en avoir chez plusieurs")
    void auth31OneIdentityPerProvider() {
        UserId user = insertUser();
        identities.link(user, identity(OAuthProvider.GOOGLE, subject(), "u@exemple.ca"), NOW);
        identities.link(user, identity(OAuthProvider.GITHUB, subject(), "u@exemple.ca"), NOW);

        assertThatThrownBy(() -> identities.link(user, identity(OAuthProvider.GOOGLE, subject(), "u@exemple.ca"), NOW))
                .isInstanceOf(OAuthException.AlreadyLinked.class);
    }

    @Test
    @DisplayName("AUTH-31 : nOAuth, le même courriel chez un autre sujet ne retrouve jamais le compte")
    void auth31SameEmailOtherSubject() {
        UserId victim = insertUser();
        identities.link(victim, identity(OAuthProvider.GOOGLE, subject(), "victime@exemple.ca"), NOW);

        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject())).isEmpty();
    }

    @Test
    @DisplayName("ACC-07 : la suppression du compte supprime ses liaisons")
    void acc07DeletedWithTheAccount() {
        UserId user = insertUser();
        String subject = subject();
        identities.link(user, identity(OAuthProvider.GOOGLE, subject, "suppr@exemple.ca"), NOW);

        jdbc.sql("DELETE FROM users WHERE id = ?").param(user.value()).update();

        assertThat(identities.findUser(OAuthProvider.GOOGLE, subject)).isEmpty();
    }
}
