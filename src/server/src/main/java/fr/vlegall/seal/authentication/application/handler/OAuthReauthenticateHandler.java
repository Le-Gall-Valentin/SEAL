package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.OAuthReauthenticateUseCase;
import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.port.out.OAuthEventPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;

/**
 * AUTH-55, ADR 0034 (point 2). Le retour n'ouvre le mode sudo que pour l'identité déjà liée au compte : même
 * fournisseur, même sujet (AUTH-31), jamais le courriel. Toute autre identité, liée à un autre compte ou à aucun, est
 * refusée et journalisée, sans rien lier (ACC-04, P1).
 */
@ApplicationService
public class OAuthReauthenticateHandler implements OAuthReauthenticateUseCase {

    private final OAuthIdentityRepository identities;
    private final OAuthEventPort journal;

    public OAuthReauthenticateHandler(OAuthIdentityRepository identities, OAuthEventPort journal) {
        this.identities = identities;
        this.journal = journal;
    }

    @Override
    public boolean reauthenticate(UserId user, ExternalIdentity identity) {
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
