package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CurrentAccountQuery;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.AccountView;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.PendingVerification;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.ResendAllowance;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CurrentAccountHandler implements CurrentAccountQuery {

    private final UserRepository users;
    private final EmailCodeRepository codes;
    private final ResendAllowance resends;
    private final Clock clock;

    public CurrentAccountHandler(
            UserRepository users, EmailCodeRepository codes, ResendAllowance resends, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.resends = resends;
        this.clock = clock;
    }

    /** Une session dont le compte n'existe plus, ou est supprimé, n'obtient rien (échec fermé, A01). */
    @Override
    @Transactional(readOnly = true)
    public AccountView current(UserId user) {
        Account account = users.findById(user)
                .filter(found -> found.status() != AccountStatus.DELETED)
                .orElseThrow(() -> new SealException(ErrorCode.UNAUTHENTICATED));
        PendingVerification verification = account.status() == AccountStatus.PENDING_EMAIL
                ? new PendingVerification(
                        codes.liveExpiry(user, clock.instant()).orElse(null), resends.remaining(user))
                : null;
        EmailAddress email = Objects.requireNonNull(account.email(), "courriel d'un compte non supprimé");
        return new AccountView(
                account.id(), account.username(), account.displayName(), email, account.status(), verification);
    }
}
