package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.InspectPasswordResetQuery;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.model.ResetView;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class InspectPasswordResetHandler implements InspectPasswordResetQuery {

    private final PasswordResetTokenRepository tokens;
    private final AccountLookupPort accounts;
    private final Clock clock;

    public InspectPasswordResetHandler(PasswordResetTokenRepository tokens, AccountLookupPort accounts, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResetView> inspect(String token) {
        return ResetToken.parse(token)
                .flatMap(parsed -> tokens.findLive(parsed.hash(), clock.instant()))
                .flatMap(live -> accounts.findActiveContact(live.user())
                        .map(contact -> new ResetView(contact.username(), contact.email(), live.expiresAt())));
    }
}
