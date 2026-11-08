package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ChangePendingEmailUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-17. L'adresse change même si un compte actif la possède : son titulaire reçoit l'avertissement à la place du
 * code, comme à l'inscription (AUTH-15). La limite partagée avec le renvoi s'applique avant, dans la couche web.
 */
@ApplicationService
public class ChangePendingEmailHandler implements ChangePendingEmailUseCase {

    private final UserRepository users;
    private final SecurityEventPort journal;
    private final CodeDelivery codes;

    public ChangePendingEmailHandler(UserRepository users, SecurityEventPort journal, CodeDelivery codes) {
        this.users = users;
        this.journal = journal;
        this.codes = codes;
    }

    @Override
    @Transactional
    public void change(UserId user, EmailAddress email) {
        Account account = AccountLocks.lockPending(users, user);
        users.changeEmail(user, email);
        journal.emailChanged(user, email);
        codes.issue(user, account.username(), email);
    }
}
