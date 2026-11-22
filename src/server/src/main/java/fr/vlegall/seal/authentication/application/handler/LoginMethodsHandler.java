package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.LoginMethodsQuery;
import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR 0035, point 2 : le compte de la session lit ses propres méthodes de connexion. La route est réservée à la session
 * complète ; le handler revérifie que le compte est encore actif (SEC-03), comme les routes du compte (ADR 0034).
 */
@ApplicationService
public class LoginMethodsHandler implements LoginMethodsQuery {

    private final CredentialRepository credentials;
    private final OAuthIdentityRepository identities;
    private final OAuthAccountLookupPort accounts;

    public LoginMethodsHandler(
            CredentialRepository credentials, OAuthIdentityRepository identities, OAuthAccountLookupPort accounts) {
        this.credentials = credentials;
        this.identities = identities;
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginMethods of(UserId user) {
        return switch (accounts.state(user)) {
            case ACTIVE -> new LoginMethods(credentials.hasPassword(user), identities.linkedProviders(user));
            // Compte supprimé ou inconnu : la session ne vaut plus rien, comme pour /me.
            case UNUSABLE -> throw new SealException(ErrorCode.UNAUTHENTICATED);
            case PENDING_EMAIL -> throw new SealException(ErrorCode.FORBIDDEN);
        };
    }
}
