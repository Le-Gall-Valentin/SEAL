package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ActiveUsernameQuery;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ActiveUsernameHandler implements ActiveUsernameQuery {

    private final UserRepository users;

    public ActiveUsernameHandler(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> activeUsername(UserId user) {
        return users.findById(user)
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .map(account -> account.username().value());
    }
}
