package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.OAuthReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.AccountLoginState;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;

/**
 * AUTH-55, ADR 0034 (point 2). Le retour n'ouvre le mode sudo que pour l'identité déjà liée au compte : même
 * fournisseur, même sujet (AUTH-31), jamais le courriel. Toute autre identité, liée à un autre compte ou à aucun, est
 * refusée et journalisée, sans rien lier (ACC-04, P1). Comme la connexion, la ré-authentification exige un compte encore
 * actif (SEC-03), et le départ n'est permis que vers un fournisseur lié au compte.
 */
@ApplicationService
public class OAuthReauthenticateHandler implements OAuthReauthenticateUseCase {

    private final OAuthIdentityRepository identities;
    private final OAuthAccountLookupPort accounts;
    private final OAuthEventPort journal;

    public OAuthReauthenticateHandler(
            OAuthIdentityRepository identities, OAuthAccountLookupPort accounts, OAuthEventPort journal) {
        this.identities = identities;
        this.accounts = accounts;
        this.journal = journal;
    }

    @Override
    public boolean canStart(UserId user, OAuthProvider provider) {
        return accounts.state(user) == AccountLoginState.ACTIVE && identities.isLinked(user, provider);
    }

    @Override
    public boolean reauthenticate(UserId user, ExternalIdentity identity) {
        if (accounts.state(user) != AccountLoginState.ACTIVE) {
            journal.oauthLoginFailed(user, identity.provider(), "account_unusable");
            return false;
        }
        boolean linked = identities
                .findUser(identity.provider(), identity.subject())
                .filter(user::equals)
                .isPresent();
        if (!linked) {
            journal.oauthLoginFailed(user, identity.provider(), "reauth_identity_mismatch");
        }
        return linked;
    }
}
