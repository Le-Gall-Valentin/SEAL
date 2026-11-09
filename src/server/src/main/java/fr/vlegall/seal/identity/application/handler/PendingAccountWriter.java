package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.PreparedPassword;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écritures de l'inscription, dans une seule transaction : compte en attente, haché déjà préparé, journal et code. La
 * réponse ne dépend pas de l'adresse, libre ou non (AUTH-15) : même travail, mêmes lignes, même nombre de courriels.
 */
@ApplicationService
public class PendingAccountWriter {

    private final UserRepository users;
    private final InitialPasswordPort credentials;
    private final SecurityEventPort journal;
    private final CodeDelivery codes;

    public PendingAccountWriter(
            UserRepository users, InitialPasswordPort credentials, SecurityEventPort journal, CodeDelivery codes) {
        this.users = users;
        this.credentials = credentials;
        this.journal = journal;
        this.codes = codes;
    }

    /** L'index unique de {@code users.username} reste juge : un identifiant pris entre-temps lève UsernameTaken. */
    @Transactional
    public UserId create(Username username, RegisterCommand command, PreparedPassword password) {
        UserId id = users.createPending(username, command.displayName(), command.email());
        credentials.store(id, password);
        journal.accountRegistered(id, command.email());
        codes.issue(id, username, command.email());
        return id;
    }
}
