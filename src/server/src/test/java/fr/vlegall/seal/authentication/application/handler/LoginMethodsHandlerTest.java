package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR 0035, point 2 : méthodes de connexion du compte, lues pour l'écran Paramètres (AUTH-55, ACC-04). */
@DisplayName("ADR 0035 : méthodes de connexion du compte")
class LoginMethodsHandlerTest {

    private final InMemoryCredentials credentials = new InMemoryCredentials();
    private final Set<String> links = new HashSet<>();

    private final OAuthIdentityRepository identities = new OAuthIdentityRepository() {
        @Override
        public Optional<UserId> findUser(OAuthProvider provider, String subject) {
            throw new UnsupportedOperationException("la lecture des méthodes ne cherche aucun sujet");
        }

        @Override
        public boolean isLinked(UserId user, OAuthProvider provider) {
            return links.contains(user.value() + ":" + provider);
        }

        @Override
        public void link(UserId user, ExternalIdentity identity, Instant linkedAt) {
            throw new UnsupportedOperationException("la lecture des méthodes ne lie rien");
        }
    };

    private final LoginMethodsHandler handler = new LoginMethodsHandler(credentials, identities);

    private static UserId user() {
        return new UserId(UUID.randomUUID());
    }

    private void link(UserId user, OAuthProvider provider) {
        links.add(user.value() + ":" + provider);
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : un compte par mot de passe seul, sans fournisseur lié")
    void auth55PasswordOnly() {
        UserId user = user();
        credentials.save(user, "haché");

        assertThat(handler.of(user)).isEqualTo(new LoginMethods(true, List.of()));
    }

    @Test
    @DisplayName("AUTH-55, ADR 0035 : un compte OAuth sans mot de passe, ses fournisseurs dans l'ordre des boutons")
    void auth55ProvidersInButtonOrder() {
        UserId user = user();
        link(user, OAuthProvider.GITHUB);
        link(user, OAuthProvider.MICROSOFT);

        assertThat(handler.of(user))
                .isEqualTo(new LoginMethods(false, List.of(OAuthProvider.MICROSOFT, OAuthProvider.GITHUB)));
    }

    @Test
    @DisplayName("SEC-03, ADR 0035 : les liaisons et le mot de passe d'un autre compte ne comptent pas")
    void sec03OtherAccountsDoNotCount() {
        UserId user = user();
        UserId other = user();
        credentials.save(other, "haché");
        link(other, OAuthProvider.GOOGLE);
        link(user, OAuthProvider.GOOGLE);

        assertThat(handler.of(user)).isEqualTo(new LoginMethods(false, List.of(OAuthProvider.GOOGLE)));
    }
}
