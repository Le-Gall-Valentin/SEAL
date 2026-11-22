package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.RequestPasswordResetUseCase;
import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.port.out.AccountContactPort;
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

    private final AccountContactPort accounts;
    private final CredentialRepository credentials;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetMailPort mail;
    private final RandomBytes random;
    private final Clock clock;

    public RequestPasswordResetHandler(
            AccountContactPort accounts,
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
     * Le verrou du compte, qui suppose un mot de passe, sérialise les demandes simultanées et le changement de courriel
     * (ACC-02) : le nouveau lien invalide les précédents et naît dans la même transaction ; le courriel part après sa
     * validation (ADR 0022, points 3 et 4 ; ADR 0034).
     */
    @Override
    @Transactional
    public void request(String email) {
        Optional<AccountContact> contact = accounts.findActiveByEmail(email);
        if (contact.isEmpty() || !credentials.lock(contact.get().user())) {
            return;
        }
        // ACC-02, ADR 0034 : l'adresse a pu changer pendant l'attente du verrou ; relue après lui, elle n'envoie jamais
        // un lien à une adresse que le compte n'a plus.
        if (!accounts.findActiveByEmail(email)
                .map(AccountContact::user)
                .filter(contact.get().user()::equals)
                .isPresent()) {
            return;
        }
        ResetToken token = ResetToken.of(random.next(ResetToken.BYTES));
        Instant now = clock.instant();
        tokens.issue(contact.get().user(), token.hash(), now.plus(ResetToken.VALIDITY), now);
        mail.sendLink(contact.get().email(), contact.get().username(), token);
    }
}
