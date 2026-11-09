package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.InitialPasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.PasswordPolicy;
import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class InitialPasswordHandler implements InitialPasswordUseCase {

    private final Blocklist blocklist;
    private final PasswordHasher hasher;
    private final CredentialRepository credentials;

    public InitialPasswordHandler(Blocklist blocklist, PasswordHasher hasher, CredentialRepository credentials) {
        this.blocklist = blocklist;
        this.hasher = hasher;
        this.credentials = credentials;
    }

    /** {@code NEVER} : un appel dans une transaction échoue au lieu de garder une connexion pendant le hachage. */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Optional<String> prepare(String password, List<String> personalWords) {
        Password candidate = Password.fromInput(password);
        if (!PasswordPolicy.accepts(candidate, personalWords, blocklist)) {
            return Optional.empty();
        }
        return Optional.of(hasher.hash(candidate));
    }

    /** {@code MANDATORY} : le mot de passe naît avec le compte, dans la même transaction, ou pas du tout. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void store(UserId user, String preparedHash) {
        credentials.save(user, preparedHash);
    }
}
