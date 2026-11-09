package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.LoginOutcome;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginHandlerTest {

    private static final String PASSWORD = "fjord-tuque-raquette";

    private final Map<String, UserId> logins = new HashMap<>();
    private final Set<UserId> verified = new HashSet<>();
    private final Map<UserId, String> hashes = new HashMap<>();
    /** Chaque vérification Argon2id, réelle ou leurre, dans l'ordre. */
    private final List<String> verifications = new ArrayList<>();

    private final AccountLookupPort accounts = new AccountLookupPort() {
        @Override
        public Optional<UserId> findForLogin(String login) {
            return Optional.ofNullable(logins.get(login));
        }

        @Override
        public boolean isVerified(UserId user) {
            return verified.contains(user);
        }
    };
    private final CredentialRepository credentials = new CredentialRepository() {
        @Override
        public void save(UserId user, String passwordHash) {
            hashes.put(user, passwordHash);
        }

        @Override
        public Optional<String> findHash(UserId user) {
            return Optional.ofNullable(hashes.get(user));
        }
    };
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(Password password) {
            return "hash(" + password.value() + ")";
        }

        @Override
        public boolean matches(Password password, String encoded) {
            verifications.add("réelle");
            return password.value().codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF)
                    && encoded.equals(hash(password));
        }

        @Override
        public boolean recognises(String encoded) {
            return encoded.startsWith("hash(");
        }

        @Override
        public void verifyDecoy(Password password) {
            verifications.add("leurre");
        }
    };
    private final LoginHandler handler = new LoginHandler(accounts, credentials, hasher);

    private UserId account(String login, boolean isVerified, boolean withPassword) {
        UserId user = new UserId(UUID.randomUUID());
        logins.put(login, user);
        if (isVerified) {
            verified.add(user);
        }
        if (withPassword) {
            hashes.put(user, "hash(" + PASSWORD + ")");
        }
        return user;
    }

    @Test
    @DisplayName("AUTH-20 : le bon mot de passe d'un compte actif l'authentifie, vérifié")
    void auth20RightPasswordOfAnActiveAccountIsAuthenticated() {
        UserId user = account("valentin", true, true);

        assertThat(handler.login("valentin", PASSWORD)).isEqualTo(new LoginOutcome.Authenticated(user, true));
        assertThat(verifications).containsExactly("réelle");
    }

    @Test
    @DisplayName("AUTH-16 : le bon mot de passe d'un compte en attente l'authentifie, non vérifié")
    void auth16RightPasswordOfAPendingAccountIsAuthenticatedUnverified() {
        UserId user = account("attente", false, true);

        assertThat(handler.login("attente", PASSWORD)).isEqualTo(new LoginOutcome.Authenticated(user, false));
    }

    @Test
    @DisplayName("AUTH-21 : une saisie inconnue est refusée après la vérification d'un leurre, au même coût")
    void auth21UnknownLoginVerifiesTheDecoy() {
        assertThat(handler.login("inconnu", PASSWORD)).isEqualTo(new LoginOutcome.Rejected(null));
        assertThat(verifications).containsExactly("leurre");
    }

    @Test
    @DisplayName("AUTH-21 : un compte sans mot de passe est refusé après la vérification d'un leurre")
    void auth21AccountWithoutPasswordVerifiesTheDecoy() {
        UserId user = account("oauth", true, false);

        assertThat(handler.login("oauth", PASSWORD)).isEqualTo(new LoginOutcome.Rejected(user));
        assertThat(verifications).containsExactly("leurre");
    }

    @Test
    @DisplayName("AUTH-21 : un mauvais mot de passe est refusé, après une seule vérification")
    void auth21WrongPasswordIsRejected() {
        UserId user = account("valentin", true, true);

        assertThat(handler.login("valentin", "mauvais-mot-de-passe")).isEqualTo(new LoginOutcome.Rejected(user));
        assertThat(verifications).containsExactly("réelle");
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe à demi-paire isolée est refusé, sans erreur")
    void auth11LoneSurrogatePasswordIsRejected() {
        UserId user = account("valentin", true, true);

        assertThat(handler.login("valentin", Character.toString(0xD800) + PASSWORD))
                .isEqualTo(new LoginOutcome.Rejected(user));
    }
}
