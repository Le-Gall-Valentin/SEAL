package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.LoginUseCase;
import fr.vlegall.seal.authentication.domain.model.LoginOutcome;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class LoginHandler implements LoginUseCase {

    private final AccountLookupPort accounts;
    private final CredentialRepository credentials;
    private final PasswordHasher hasher;

    public LoginHandler(AccountLookupPort accounts, CredentialRepository credentials, PasswordHasher hasher) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.hasher = hasher;
    }

    /**
     * {@code NEVER} : Argon2id et l'attente de son sémaphore ne retiennent aucune connexion (ADR 0020). Un compte
     * inconnu, supprimé ou sans mot de passe coûte la vérification d'un leurre, comme un compte existant (AUTH-21).
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public LoginOutcome login(String login, String password) {
        Password candidate = Password.fromInput(password);
        Optional<UserId> account = accounts.findForLogin(login);
        Optional<String> hash = account.flatMap(credentials::findHash);
        if (account.isEmpty() || hash.isEmpty()) {
            hasher.verifyDecoy(candidate);
            return new LoginOutcome.Rejected(account.orElse(null));
        }
        UserId user = account.get();
        if (!hasher.matches(candidate, hash.get())) {
            return new LoginOutcome.Rejected(user);
        }
        return new LoginOutcome.Authenticated(user, accounts.isVerified(user));
    }
}
