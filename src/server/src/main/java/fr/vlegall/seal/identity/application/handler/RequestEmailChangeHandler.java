package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.RequestEmailChangeUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import fr.vlegall.seal.identity.domain.port.out.SudoModePort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACC-02, ADR 0034. Le mode sudo est exigé ici (SEC-03, AUTH-55). Comme pour l'inscription (AUTH-15), le code est
 * toujours enregistré, mais si un compte actif possède l'adresse, son titulaire reçoit un avertissement à la place :
 * la réponse ne révèle pas si une adresse a un compte (point 3). La limite des envois est prise avant, dans la couche
 * web (point 4).
 */
@ApplicationService
public class RequestEmailChangeHandler implements RequestEmailChangeUseCase {

    private final UserRepository users;
    private final EmailCodeRepository codes;
    private final AccountMailPort mail;
    private final RandomDigits random;
    private final Clock clock;
    private final SudoModePort sudo;

    public RequestEmailChangeHandler(
            UserRepository users,
            EmailCodeRepository codes,
            AccountMailPort mail,
            RandomDigits random,
            Clock clock,
            SudoModePort sudo) {
        this.users = users;
        this.codes = codes;
        this.mail = mail;
        this.random = random;
        this.clock = clock;
        this.sudo = sudo;
    }

    @Override
    @Transactional
    public void request(UserId user, EmailAddress email) {
        sudo.require();
        Account account = AccountLocks.lockActive(users, user);
        // La base compare les adresses sans tenir compte de la casse (citext), comme l'index users_verified_email.
        Optional<UserId> holder = users.findActiveByEmail(email);
        // L'adresse actuelle n'est pas un changement : rien ne part.
        if (holder.filter(user::equals).isPresent()) {
            throw new SealException(ErrorCode.VALIDATION_FAILED, "email");
        }
        VerificationCode code = VerificationCode.generate(random);
        Instant now = clock.instant();
        codes.renewChange(user, email, code.hash(), now, now.plus(CodePolicy.VALIDITY));
        if (holder.isPresent()) {
            mail.sendEmailTakenWarning(email);
        } else {
            mail.sendEmailChangeCode(email, account.username(), code);
        }
    }
}
