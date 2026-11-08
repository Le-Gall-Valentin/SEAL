package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.SetUpPasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.PasswordPolicy;
import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.List;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class SetUpPasswordHandler implements SetUpPasswordUseCase {

    private final Blocklist blocklist;
    private final PasswordHasher hasher;
    private final CredentialRepository credentials;

    public SetUpPasswordHandler(Blocklist blocklist, PasswordHasher hasher, CredentialRepository credentials) {
        this.blocklist = blocklist;
        this.hasher = hasher;
        this.credentials = credentials;
    }

    /** {@code MANDATORY} : le mot de passe naît avec le compte, dans la même transaction, ou pas du tout. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean setUp(UserId user, String password, List<String> personalWords) {
        Password candidate = Password.fromInput(password);
        if (!PasswordPolicy.accepts(candidate, personalWords, blocklist)) {
            return false;
        }
        credentials.save(user, hasher.hash(candidate));
        return true;
    }
}
