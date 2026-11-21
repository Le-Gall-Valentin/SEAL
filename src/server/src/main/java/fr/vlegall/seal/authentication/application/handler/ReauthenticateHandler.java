package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.ReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.AccountContactPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-55, ADR 0034 : le mot de passe d'une session complète est vérifié de nouveau. Un compte qui n'est plus actif ou
 * qui n'a pas de mot de passe coûte la vérification d'un leurre, comme à la connexion (AUTH-21). Un refus est
 * journalisé comme un échec de connexion (ADR 0034, point 4).
 */
@ApplicationService
public class ReauthenticateHandler implements ReauthenticateUseCase {

    private final AccountContactPort accounts;
    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final SecurityEventPort journal;

    public ReauthenticateHandler(
            AccountContactPort accounts,
            CredentialRepository credentials,
            PasswordHasher hasher,
            SecurityEventPort journal) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.hasher = hasher;
        this.journal = journal;
    }

    /** {@code NEVER} : Argon2id ne retient aucune connexion (ADR 0020). */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public boolean reauthenticate(UserId user, String password) {
        Password candidate = Password.fromInput(password);
        Optional<StoredCredential> stored = accounts.findActiveContact(user).flatMap(contact -> credentials.find(user));
        if (stored.isEmpty()) {
            hasher.verifyDecoy(candidate);
            journal.loginFailed(user);
            return false;
        }
        if (!hasher.matches(candidate, stored.get().hash())) {
            journal.loginFailed(user);
            return false;
        }
        return true;
    }
}
