package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.CompleteOAuthRegistrationUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthRegistration;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-32 : compte créé, identité liée et liaison journalisée, tout ou rien. */
@ApplicationService
public class CompleteOAuthRegistrationHandler implements CompleteOAuthRegistrationUseCase {

    private final OAuthAccountPort accounts;
    private final OAuthIdentityRepository identities;
    private final SecurityEventPort journal;
    private final Clock clock;

    public CompleteOAuthRegistrationHandler(
            OAuthAccountPort accounts, OAuthIdentityRepository identities, SecurityEventPort journal, Clock clock) {
        this.accounts = accounts;
        this.identities = identities;
        this.journal = journal;
        this.clock = clock;
    }

    /**
     * Une seule transaction (SEC-20, ADR 0013, point 6) : si l'identité a été liée entre-temps, par un second
     * {@code complete} de la même attente, le compte créé disparaît avec elle.
     */
    @Override
    @Transactional
    public OAuthRegistration complete(ExternalIdentity identity, String username, String displayName) {
        UserId user = accounts.create(username, displayName, identity.email(), identity.emailVerified());
        identities.link(user, identity, clock.instant());
        journal.oauthLinked(user, identity.provider());
        return new OAuthRegistration(user, identity.emailVerified());
    }
}
