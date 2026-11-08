package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.RegisterUseCase;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;
import fr.vlegall.seal.identity.domain.model.ReservedUsernames;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class RegisterHandler implements RegisterUseCase {

    private final UserRepository users;
    private final InitialPasswordPort credentials;
    private final SecurityEventPort journal;
    private final CodeDelivery codes;

    public RegisterHandler(
            UserRepository users, InitialPasswordPort credentials, SecurityEventPort journal, CodeDelivery codes) {
        this.users = users;
        this.credentials = credentials;
        this.journal = journal;
        this.codes = codes;
    }

    /**
     * Une seule transaction : un refus du mot de passe annule le compte déjà inséré. La réponse ne dépend pas de
     * l'adresse, libre ou non (AUTH-15) : même travail, même nombre de lignes et de courriels.
     */
    @Override
    @Transactional
    public UserId register(RegisterCommand command) {
        Username username = Username.parse(command.username());
        if (ReservedUsernames.contains(username.value())) {
            throw new IdentityException.UsernameReserved();
        }
        UserId id = users.createPending(username, command.displayName(), command.email());
        List<String> personalWords = List.of(username.value(), command.email().localPart());
        if (!credentials.establish(id, command.password(), personalWords)) {
            throw new IdentityException.PasswordTooWeak();
        }
        journal.accountRegistered(id, command.email());
        codes.issue(id, username, command.email());
        return id;
    }
}
