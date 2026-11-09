package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.VerifyEmailUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailVerification;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class VerifyEmailHandler implements VerifyEmailUseCase {

    private final UserRepository users;
    private final EmailCodeRepository codes;
    private final Clock clock;

    public VerifyEmailHandler(UserRepository users, EmailCodeRepository codes, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * Le compte est verrouillé d'abord : deux vérifications simultanées du bon code donnent un succès, puis
     * {@code NoLiveCode}. Un essai manqué est compté et validé avec la transaction. Seule la ligne du compte est
     * verrouillée : deux comptes qui vérifient la même adresse ne s'attendent que sur l'index partiel.
     */
    @Override
    @Transactional
    public EmailVerification verify(UserId user, VerificationCode code) {
        Account account = AccountLocks.lock(users, user);
        if (account.status() != AccountStatus.PENDING_EMAIL) {
            return new EmailVerification.NoLiveCode();
        }
        Instant now = clock.instant();
        Optional<LiveCode> live = codes.findLive(user, now);
        if (live.isEmpty()) {
            return new EmailVerification.NoLiveCode();
        }
        if (!live.get().isMatchedBy(code)) {
            codes.countFailedAttempt(live.get().id());
            return new EmailVerification.InvalidCode();
        }
        codes.consume(live.get().id(), now);
        // AUTH-15 : si un autre compte a activé la même adresse entre-temps, l'index partiel refuse cette activation
        // (CodeExpired, la transaction est annulée). Les autres comptes en attente ne sont pas touchés : rien ne
        // signale à l'un d'eux que l'adresse vient d'être activée (ADR 0019, point 1).
        users.activate(user, now);
        return new EmailVerification.Verified();
    }
}
