package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-55, ADR 0034 (point 2) : la ré-authentification OAuth n'ouvre le sudo que pour l'identité liée au compte. */
@DisplayName("AUTH-55 : ré-authentification par OAuth")
class OAuthReauthenticateHandlerTest {

    private final Map<UserId, AccountLoginState> states = new HashMap<>();
    private final List<String> events = new ArrayList<>();

    private final InMemoryOAuthIdentities identities = new InMemoryOAuthIdentities();

    {
        identities.forbidLinks("la ré-authentification ne lie rien (ACC-04, P1)");
    }

    private final OAuthAccountLookupPort accounts = new OAuthAccountLookupPort() {
        @Override
        public AccountLoginState state(UserId user) {
            return states.getOrDefault(user, AccountLoginState.UNUSABLE);
        }

        @Override
        public boolean activeAccountOwns(String email) {
            throw new UnsupportedOperationException("la ré-authentification ne cherche aucun courriel");
        }
    };

    private final OAuthEventPort journal = new OAuthEventPort() {
        @Override
        public void oauthLoginSucceeded(UserId user, OAuthProvider provider) {
            events.add("réussie:" + user.value());
        }

        @Override
        public void oauthLoginFailed(@Nullable UserId user, @Nullable OAuthProvider provider, String cause) {
            events.add("refusée:" + (user == null ? "aucun" : user.value()) + ":" + provider + ":" + cause);
        }
    };

    private final OAuthReauthenticateHandler handler = new OAuthReauthenticateHandler(identities, accounts, journal);

    private UserId account(AccountLoginState state) {
        UserId user = new UserId(UUID.randomUUID());
        states.put(user, state);
        return user;
    }

    private static ExternalIdentity identity(OAuthProvider provider, String subject) {
        return ExternalIdentity.of(provider, subject, "quelquun@exemple.ca", true, "Nom");
    }

    @Test
    @DisplayName("AUTH-55 : l'identité liée au compte de la session ouvre le sudo, sans rien journaliser")
    void auth55LinkedIdentityOfTheSameAccount() {
        UserId user = account(AccountLoginState.ACTIVE);
        identities.put(OAuthProvider.GOOGLE, "sujet-a", user);

        assertThat(handler.canStart(user, OAuthProvider.GOOGLE)).isTrue();
        assertThat(handler.reauthenticate(user, identity(OAuthProvider.GOOGLE, "sujet-a")))
                .isTrue();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("AUTH-55 : l'identité liée à un autre compte est refusée et journalisée sur le compte de la session")
    void auth55IdentityOfAnotherAccountIsRefused() {
        UserId user = account(AccountLoginState.ACTIVE);
        UserId other = account(AccountLoginState.ACTIVE);
        identities.put(OAuthProvider.GOOGLE, "sujet-a", user);
        identities.put(OAuthProvider.GOOGLE, "sujet-b", other);

        assertThat(handler.reauthenticate(user, identity(OAuthProvider.GOOGLE, "sujet-b")))
                .isFalse();
        assertThat(events).containsExactly("refusée:" + user.value() + ":GOOGLE:reauth_identity_mismatch");
    }

    @Test
    @DisplayName("AUTH-55 : une identité liée à personne, ou le même sujet chez un autre fournisseur, est refusée")
    void auth55UnlinkedOrOtherProviderIsRefused() {
        UserId user = account(AccountLoginState.ACTIVE);
        identities.put(OAuthProvider.GOOGLE, "sujet-a", user);

        assertThat(handler.reauthenticate(user, identity(OAuthProvider.GOOGLE, "inconnu")))
                .isFalse();
        assertThat(handler.reauthenticate(user, identity(OAuthProvider.GITHUB, "sujet-a")))
                .isFalse();
        assertThat(events).hasSize(2).allMatch(event -> event.endsWith(":reauth_identity_mismatch"));
    }

    @Test
    @DisplayName("SEC-03 : un compte qui n'est plus actif ne rouvre pas le sudo, même avec son identité liée")
    void sec03InactiveAccountIsRefused() {
        for (AccountLoginState state : List.of(AccountLoginState.PENDING_EMAIL, AccountLoginState.UNUSABLE)) {
            UserId user = account(state);
            identities.put(OAuthProvider.GOOGLE, "sujet-" + state, user);

            assertThat(handler.canStart(user, OAuthProvider.GOOGLE))
                    .as(state.name())
                    .isFalse();
            assertThat(handler.reauthenticate(user, identity(OAuthProvider.GOOGLE, "sujet-" + state)))
                    .as(state.name())
                    .isFalse();
        }
        assertThat(events).hasSize(2).allMatch(event -> event.endsWith(":account_unusable"));
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : le départ vers un fournisseur que le compte n'a pas lié est refusé d'emblée")
    void adr0034StartToAnUnlinkedProviderIsRefused() {
        UserId user = account(AccountLoginState.ACTIVE);
        identities.put(OAuthProvider.GOOGLE, "sujet-a", user);

        assertThat(handler.canStart(user, OAuthProvider.GITHUB)).isFalse();
    }
}
