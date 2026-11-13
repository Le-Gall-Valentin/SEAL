package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class AccountLookupHandler implements AccountLookupQuery {

    private final UserRepository users;

    public AccountLookupHandler(UserRepository users) {
        this.users = users;
    }

    /** Une saisie qui a la forme d'un identifiant le cherche ; sinon, une saisie qui a la forme d'un courriel. */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserId> findForLogin(String login) {
        String folded = Unicode.foldLogin(login);
        Optional<Username> username = Username.tryParse(folded);
        if (username.isPresent()) {
            return users.findNotDeletedByUsername(username.get());
        }
        return EmailAddress.parse(folded).flatMap(users::findActiveByEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVerified(UserId user) {
        return users.findById(user)
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .isPresent();
    }
}
