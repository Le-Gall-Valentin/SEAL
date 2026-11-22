package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ConfirmEmailChangeUseCase;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.EmailChangeConfirmation;
import fr.vlegall.seal.identity.domain.model.LiveEmailChange;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.PasswordResetLinksPort;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACC-02, ADR 0034. Le compte est verrouillé d'abord : deux confirmations simultanées du bon code donnent un succès,
 * puis {@code NoLiveCode}. Un essai manqué est compté et validé avec la transaction. Seul le détenteur du bon code,
 * donc de la nouvelle boîte, change l'adresse ; un compte qui l'a activée entre-temps l'emporte (point 3).
 */
@ApplicationService
public class ConfirmEmailChangeHandler implements ConfirmEmailChangeUseCase {

    private final UserRepository users;
    private final EmailCodeRepository codes;
    private final AccountMailPort mail;
    private final SecurityEventPort journal;
    private final PasswordResetLinksPort resetLinks;
    private final Clock clock;

    public ConfirmEmailChangeHandler(
            UserRepository users,
            EmailCodeRepository codes,
            AccountMailPort mail,
            SecurityEventPort journal,
            PasswordResetLinksPort resetLinks,
            Clock clock) {
        this.users = users;
        this.codes = codes;
        this.mail = mail;
        this.journal = journal;
        this.resetLinks = resetLinks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EmailChangeConfirmation confirm(UserId user, VerificationCode code) {
        Account account = AccountLocks.lockActive(users, user);
        Instant now = clock.instant();
        Optional<LiveEmailChange> live = codes.findLiveChange(user, now);
        if (live.isEmpty()) {
            return new EmailChangeConfirmation.NoLiveCode();
        }
        if (!live.get().isMatchedBy(code)) {
            codes.countFailedAttempt(live.get().id());
            return new EmailChangeConfirmation.InvalidCode();
        }
        codes.consume(live.get().id(), now);
        EmailAddress target = live.get().target();
        // AUTH-15 : un autre compte a activé l'adresse entre-temps. Le code est consommé et la réponse est celle d'un
        // code mort, comme à la vérification (ADR 0019, point 1).
        if (users.activeAccountOwns(target)) {
            return new EmailChangeConfirmation.NoLiveCode();
        }
        EmailAddress old = Objects.requireNonNull(account.email(), "courriel d'un compte actif");
        // Pendant une course réelle, l'index partiel refuse le second (CodeExpired, transaction annulée).
        users.changeActiveEmail(user, target, now);
        journal.emailChanged(user, target);
        resetLinks.invalidate(user);
        mail.sendEmailChangedNotice(old, account.username(), target);
        return new EmailChangeConfirmation.Changed();
    }
}
