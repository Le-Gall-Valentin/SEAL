package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Utilisateur Google (OIDC, section 5.4 du CDC) : le service de Spring, dont l'appel à {@code userinfo} est borné dans
 * le temps (A10), puis le contrôle de l'identité, pour qu'un refus passe par l'échec du retour OAuth.
 */
final class GoogleUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService users = new OidcUserService();

    GoogleUserService() {
        DefaultOAuth2UserService userInfo = new DefaultOAuth2UserService();
        userInfo.setRestOperations(ProviderHttp.restTemplate());
        users.setOauth2UserService(userInfo);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        if (!OAuthProvider.GOOGLE
                .registrationId()
                .equals(request.getClientRegistration().getRegistrationId())) {
            throw ExternalIdentities.invalid();
        }
        OidcUser user = users.loadUser(request);
        if (user == null) {
            throw ExternalIdentities.invalid();
        }
        ExternalIdentities.from(OAuthProvider.GOOGLE, user);
        return user;
    }
}
