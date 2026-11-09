package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émet le code d'un compte en attente, pour l'inscription, le renvoi et le changement d'adresse (AUTH-14, AUTH-17). La
 * règle d'AUTH-15 vit ici, une seule fois : le code est toujours enregistré, mais si un compte actif possède l'adresse,
 * son titulaire reçoit l'avertissement à la place (ADR 0019, point 1).
 */
@ApplicationService
public class CodeDelivery {

    private final UserRepository users;
    private final EmailCodeRepository codes;
    private final AccountMailPort mail;
    private final RandomDigits random;
    private final Clock clock;

    public CodeDelivery(
            UserRepository users, EmailCodeRepository codes, AccountMailPort mail, RandomDigits random, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.mail = mail;
        this.random = random;
        this.clock = clock;
    }

    /** Remplace le code vivant du compte par un nouveau, valable 10 minutes, dans la transaction de l'appelant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(UserId user, Username username, EmailAddress email) {
        VerificationCode code = VerificationCode.generate(random);
        Instant now = clock.instant();
        codes.renew(user, code.hash(), now, now.plus(CodePolicy.VALIDITY));
        if (users.activeAccountOwns(email)) {
            mail.sendExistingAccountWarning(email);
        } else {
            mail.sendCode(email, username, code);
        }
    }
}
