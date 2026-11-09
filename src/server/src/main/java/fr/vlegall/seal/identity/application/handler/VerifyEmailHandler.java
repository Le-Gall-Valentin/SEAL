package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.VerifyEmailUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.EmailVerification;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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
        // AUTH-15 : un autre compte a activé l'adresse entre-temps. Le code est consommé et la réponse est celle d'un
        // code mort ; seul le détenteur du bon code, donc de la boîte, arrive ici (ADR 0019, point 1).
        EmailAddress email = Objects.requireNonNull(account.email(), "courriel d'un compte en attente");
        if (users.activeAccountOwns(email)) {
            return new EmailVerification.NoLiveCode();
        }
        // Pendant une course réelle, l'index partiel refuse la seconde activation (CodeExpired, transaction annulée).
        // Les autres comptes en attente ne sont pas touchés : rien ne leur signale l'activation.
        users.activate(user, now);
        return new EmailVerification.Verified();
    }
}
