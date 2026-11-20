package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.OAuthLoginUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * AUTH-30 à AUTH-33, sans transaction propre : rien n'est écrit hors du journal, qui a la sienne (ADR 0021, point 7),
 * comme pour la connexion par mot de passe.
 */
@ApplicationService
public class OAuthLoginHandler implements OAuthLoginUseCase {

    private final OAuthIdentityRepository identities;
    private final AccountLookupPort accounts;
    private final OAuthEventPort journal;

    public OAuthLoginHandler(OAuthIdentityRepository identities, AccountLookupPort accounts, OAuthEventPort journal) {
        this.identities = identities;
        this.accounts = accounts;
        this.journal = journal;
    }

    /**
     * Le compte se retrouve par le couple (fournisseur, sujet), jamais par le courriel (AUTH-31). Sans compte lié, un
     * courriel vérifié d'un compte actif refuse la liaison automatique (AUTH-33) ; un courriel non vérifié mène à
     * l'attente sans rien dire de l'adresse (AUTH-15, ADR 0032, point 1).
     */
    @Override
    public OAuthLoginOutcome resolve(ExternalIdentity identity) {
        OAuthProvider provider = identity.provider();
        Optional<UserId> linked = identities.findUser(provider, identity.subject());
        if (linked.isPresent()) {
            UserId user = linked.get();
            AccountLoginState state = accounts.state(user);
            if (state == AccountLoginState.UNUSABLE) {
                journal.oauthLoginFailed(user, provider, "account_unusable");
                return new OAuthLoginOutcome.Rejected();
            }
            journal.oauthLoginSucceeded(user, provider);
            return new OAuthLoginOutcome.SignedIn(user, state == AccountLoginState.ACTIVE);
        }
        if (identity.emailVerified() && accounts.activeAccountOwns(identity.email())) {
            journal.oauthLoginFailed(null, provider, "email_in_use");
            return new OAuthLoginOutcome.EmailInUse();
        }
        return new OAuthLoginOutcome.Unlinked(identity);
    }

    @Override
    public void rejected(@Nullable OAuthProvider provider, String cause) {
        journal.oauthLoginFailed(null, provider, cause);
    }
}
