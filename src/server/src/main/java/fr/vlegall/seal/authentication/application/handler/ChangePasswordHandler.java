package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.ChangePasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.PasswordChangeOutcome;
import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.AccountContactPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.authentication.domain.port.out.SessionRevocationPort;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACC-01, ADR 0034. Le mot de passe actuel vaut ré-authentification : faux, il est refusé et journalisé comme un échec
 * de connexion ; un compte sans mot de passe coûte un leurre et ne peut pas en définir un (ACC-04, P1). Le nouveau suit
 * AUTH-11. La version n'augmente, et les autres sessions ne sont révoquées, que si l'option est cochée (ADR 0034,
 * point 1).
 */
@ApplicationService
public class ChangePasswordHandler implements ChangePasswordUseCase {

    private final AccountContactPort accounts;
    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final PasswordPreparation preparation;
    private final PasswordChangeWriter writer;
    private final SessionRevocationPort sessions;
    private final SecurityEventPort journal;

    public ChangePasswordHandler(
            AccountContactPort accounts,
            CredentialRepository credentials,
            PasswordHasher hasher,
            PasswordPreparation preparation,
            PasswordChangeWriter writer,
            SessionRevocationPort sessions,
            SecurityEventPort journal) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.hasher = hasher;
        this.preparation = preparation;
        this.writer = writer;
        this.sessions = sessions;
        this.journal = journal;
    }

    /**
     * {@code NEVER} : Argon2id ne retient aucune connexion (ADR 0020). Les écritures se font dans la transaction du
     * {@link PasswordChangeWriter} ; la révocation suit sa validation.
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PasswordChangeOutcome change(
            UserId user,
            String currentPassword,
            String newPassword,
            boolean signOutOtherSessions,
            String currentSession) {
        Password current = Password.fromInput(currentPassword);
        Optional<AccountContact> contact = accounts.findActiveContact(user);
        Optional<StoredCredential> stored = contact.flatMap(found -> credentials.find(user));
        if (contact.isEmpty() || stored.isEmpty()) {
            hasher.verifyDecoy(current);
            journal.loginFailed(user);
            return new PasswordChangeOutcome.InvalidCurrentPassword();
        }
        if (!hasher.matches(current, stored.get().hash())) {
            journal.loginFailed(user);
            return new PasswordChangeOutcome.InvalidCurrentPassword();
        }
        Optional<String> hash = preparation.prepare(newPassword, contact.get().personalWords());
        if (hash.isEmpty()) {
            return new PasswordChangeOutcome.WeakPassword();
        }
        OptionalLong version = writer.apply(contact.get(), stored.get().hash(), hash.get(), signOutOtherSessions);
        if (version.isEmpty()) {
            return new PasswordChangeOutcome.InvalidCurrentPassword();
        }
        if (signOutOtherSessions) {
            sessions.revokeOthers(user, currentSession);
        }
        return new PasswordChangeOutcome.Changed(version.getAsLong());
    }
}
