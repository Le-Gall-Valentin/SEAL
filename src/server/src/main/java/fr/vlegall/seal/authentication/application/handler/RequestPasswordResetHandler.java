package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.RequestPasswordResetUseCase;
import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetMailPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.authentication.domain.port.out.RandomBytes;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class RequestPasswordResetHandler implements RequestPasswordResetUseCase {

    private final AccountLookupPort accounts;
    private final CredentialRepository credentials;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetMailPort mail;
    private final RandomBytes random;
    private final Clock clock;

    public RequestPasswordResetHandler(
            AccountLookupPort accounts,
            CredentialRepository credentials,
            PasswordResetTokenRepository tokens,
            PasswordResetMailPort mail,
            RandomBytes random,
            Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.tokens = tokens;
        this.mail = mail;
        this.random = random;
        this.clock = clock;
    }

    /**
     * Le nouveau lien invalide les précédents et naît dans la même transaction ; le courriel part après sa validation
     * (ADR 0022, points 3 et 4).
     */
    @Override
    @Transactional
    public void request(String email) {
        Optional<AccountContact> contact = accounts.findActiveByEmail(email)
                .filter(found -> credentials.findHash(found.user()).isPresent());
        if (contact.isEmpty()) {
            return;
        }
        ResetToken token = ResetToken.of(random.next(ResetToken.BYTES));
        Instant now = clock.instant();
        tokens.issue(contact.get().user(), token.hash(), now.plus(ResetToken.VALIDITY), now);
        mail.sendLink(contact.get().email(), contact.get().username(), token);
    }
}
