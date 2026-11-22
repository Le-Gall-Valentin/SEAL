package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.AccountContactPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-55, ACC-01, ADR 0034 : vérifie le mot de passe d'une session, pour la ré-authentification et le changement de
 * mot de passe. Un compte qui n'est plus actif ou qui n'a pas de mot de passe coûte la vérification d'un leurre, comme
 * à la connexion (AUTH-21) ; un refus est journalisé comme un échec de connexion (point 4).
 */
@ApplicationService
public class CurrentPasswordCheck {

    /** Compte actif et mot de passe enregistré dont la saisie vient d'être vérifiée. */
    public record Verified(AccountContact contact, StoredCredential stored) {

        public Verified {
            Objects.requireNonNull(contact, "contact");
            Objects.requireNonNull(stored, "stored");
        }
    }

    private final AccountContactPort accounts;
    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final SecurityEventPort journal;

    public CurrentPasswordCheck(
            AccountContactPort accounts,
            CredentialRepository credentials,
            PasswordHasher hasher,
            SecurityEventPort journal) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.hasher = hasher;
        this.journal = journal;
    }

    /**
     * {@code NEVER} : Argon2id ne retient aucune connexion (ADR 0020).
     *
     * @return le compte et le mot de passe vérifiés ; vide si la saisie est refusée
     */
    @Transactional(propagation = Propagation.NEVER)
    public Optional<Verified> verify(UserId user, String password) {
        Password candidate = Password.fromInput(password);
        Optional<AccountContact> contact = accounts.findActiveContact(user);
        Optional<StoredCredential> stored = contact.flatMap(found -> credentials.find(user));
        if (contact.isEmpty() || stored.isEmpty()) {
            hasher.verifyDecoy(candidate);
            journal.loginFailed(user);
            return Optional.empty();
        }
        if (!hasher.matches(candidate, stored.get().hash())) {
            journal.loginFailed(user);
            return Optional.empty();
        }
        return Optional.of(new Verified(contact.get(), stored.get()));
    }
}
