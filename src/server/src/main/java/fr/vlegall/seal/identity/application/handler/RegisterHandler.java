package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.RegisterUseCase;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.PreparedPassword;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;
import fr.vlegall.seal.identity.domain.model.ReservedUsernames;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.List;

@ApplicationService
public class RegisterHandler implements RegisterUseCase {

    private final UserRepository users;
    private final InitialPasswordPort credentials;
    private final PendingAccountWriter writer;

    public RegisterHandler(UserRepository users, InitialPasswordPort credentials, PendingAccountWriter writer) {
        this.users = users;
        this.credentials = credentials;
        this.writer = writer;
    }

    /**
     * Le mot de passe est vérifié et haché avant la transaction, qui ne s'ouvre qu'une fois tout validé (ADR 0019,
     * point 3) : Argon2id ne retient aucune connexion à la base. Les refus gardent leur ordre : identifiant invalide,
     * réservé, pris, puis mot de passe trop faible ; un identifiant pris l'est sans calcul d'Argon2id.
     *
     * <p>Sans {@code @Transactional} : rien ici n'a à être atomique, et {@code InitialPasswordUseCase.prepare} refuse
     * déjà d'être appelé dans une transaction. Hors transaction réelle, la connexion prise par {@code users.exists} est
     * rendue aussitôt (ADR 0020).
     */
    @Override
    public UserId register(RegisterCommand command) {
        Username username = Username.parse(command.username());
        if (ReservedUsernames.contains(username.value())) {
            throw new IdentityException.UsernameReserved();
        }
        if (users.exists(username)) {
            throw new IdentityException.UsernameTaken();
        }
        List<String> personalWords = List.of(username.value(), command.email().localPart());
        PreparedPassword password = credentials
                .prepare(command.password(), personalWords)
                .orElseThrow(IdentityException.PasswordTooWeak::new);
        return writer.create(username, command, password);
    }
}
