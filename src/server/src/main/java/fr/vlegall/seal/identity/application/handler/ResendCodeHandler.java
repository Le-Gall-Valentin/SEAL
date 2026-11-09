package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ResendCodeUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** La limite de 3 renvois par heure s'applique avant, dans la couche web (SEC-10). */
@ApplicationService
public class ResendCodeHandler implements ResendCodeUseCase {

    private final UserRepository users;
    private final CodeDelivery codes;

    public ResendCodeHandler(UserRepository users, CodeDelivery codes) {
        this.users = users;
        this.codes = codes;
    }

    @Override
    @Transactional
    public void resend(UserId user) {
        Account account = AccountLocks.lockPending(users, user);
        EmailAddress email = Objects.requireNonNull(account.email(), "courriel d'un compte en attente");
        codes.issue(user, account.username(), email);
    }
}
