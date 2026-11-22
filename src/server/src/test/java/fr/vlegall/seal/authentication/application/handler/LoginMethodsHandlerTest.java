package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountLookupPort;
import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR 0035, point 2 : méthodes de connexion du compte, lues pour l'écran Paramètres (AUTH-55, ACC-04). */
@DisplayName("AUTH-55, ADR 0035 : méthodes de connexion du compte")
class LoginMethodsHandlerTest {

    private final InMemoryCredentials credentials = new InMemoryCredentials();
    private final InMemoryOAuthIdentities identities = new InMemoryOAuthIdentities();
    private final Map<UserId, AccountLoginState> states = new HashMap<>();

    private final OAuthAccountLookupPort accounts = new OAuthAccountLookupPort() {
        @Override
        public AccountLoginState state(UserId user) {
            return states.getOrDefault(user, AccountLoginState.UNUSABLE);
        }

        @Override
        public boolean activeAccountOwns(String email) {
            throw new UnsupportedOperationException("la lecture des méthodes ne cherche aucun courriel");
        }
    };

    private final LoginMethodsHandler handler = new LoginMethodsHandler(credentials, identities, accounts);

    private UserId account(AccountLoginState state) {
        UserId user = new UserId(UUID.randomUUID());
        states.put(user, state);
        return user;
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : un compte par mot de passe seul, sans fournisseur lié")
    void auth55PasswordOnly() {
        UserId user = account(AccountLoginState.ACTIVE);
        credentials.save(user, "haché");

        assertThat(handler.of(user)).isEqualTo(new LoginMethods(true, List.of()));
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : un compte OAuth sans mot de passe, ses fournisseurs dans l'ordre des boutons")
    void auth55ProvidersInButtonOrder() {
        UserId user = account(AccountLoginState.ACTIVE);
        identities.put(OAuthProvider.GITHUB, "sujet-gh", user);
        identities.put(OAuthProvider.MICROSOFT, "sujet-ms", user);

        assertThat(handler.of(user))
                .isEqualTo(new LoginMethods(false, List.of(OAuthProvider.MICROSOFT, OAuthProvider.GITHUB)));
    }

    @Test
    @DisplayName("SEC-03, ADR 0035 : les liaisons et le mot de passe d'un autre compte ne comptent pas")
    void sec03OtherAccountsDoNotCount() {
        UserId user = account(AccountLoginState.ACTIVE);
        UserId other = account(AccountLoginState.ACTIVE);
        credentials.save(other, "haché");
        identities.put(OAuthProvider.GOOGLE, "sujet-autre", other);
        identities.put(OAuthProvider.GOOGLE, "sujet-moi", user);

        assertThat(handler.of(user)).isEqualTo(new LoginMethods(false, List.of(OAuthProvider.GOOGLE)));
    }

    @Test
    @DisplayName("SEC-03, ADR 0035 : un compte qui n'est plus actif n'est pas lu, même avec une session ouverte")
    void sec03InactiveAccountIsRefused() {
        UserId deleted = account(AccountLoginState.UNUSABLE);
        UserId pending = account(AccountLoginState.PENDING_EMAIL);
        credentials.save(deleted, "haché");

        assertThatThrownBy(() -> handler.of(deleted))
                .isInstanceOfSatisfying(
                        SealException.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> handler.of(pending))
                .isInstanceOfSatisfying(
                        SealException.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
