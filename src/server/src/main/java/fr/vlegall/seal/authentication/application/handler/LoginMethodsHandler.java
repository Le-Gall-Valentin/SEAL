package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.LoginMethodsQuery;
import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.OAuthIdentityRepository;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.Arrays;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR 0035, point 2 : le compte de la session lit ses propres méthodes de connexion ; la route est réservée à la
 * session complète. Une requête par fournisseur connu, trois au plus.
 */
@ApplicationService
public class LoginMethodsHandler implements LoginMethodsQuery {

    private final CredentialRepository credentials;
    private final OAuthIdentityRepository identities;

    public LoginMethodsHandler(CredentialRepository credentials, OAuthIdentityRepository identities) {
        this.credentials = credentials;
        this.identities = identities;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginMethods of(UserId user) {
        return new LoginMethods(
                credentials.find(user).isPresent(),
                Arrays.stream(OAuthProvider.values())
                        .filter(provider -> identities.isLinked(user, provider))
                        .toList());
    }
}
