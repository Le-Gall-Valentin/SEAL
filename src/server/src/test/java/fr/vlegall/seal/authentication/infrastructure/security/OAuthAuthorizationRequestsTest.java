package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SealPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * AUTH-30, ADR 0032 (point 7) : requête d'autorisation rendue à la seule session anonyme, pour le fournisseur du
 * départ.
 */
@DisplayName("AUTH-30 : dépôt des requêtes d'autorisation")
class OAuthAuthorizationRequestsTest {

    private static final String STATE = "etat-du-depart";

    private final OAuthAuthorizationRequests requests = new OAuthAuthorizationRequests();
    private final MockHttpSession session = new MockHttpSession();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Le départ Google, gardé dans la session. */
    private void startGoogle() {
        MockHttpServletRequest start = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        start.setSession(session);
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://fournisseur.exemple/authorize")
                .clientId("client")
                .redirectUri("https://localhost:8443/login/oauth2/code/google")
                .state(STATE)
                .attributes(attributes -> attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "google"))
                .build();
        requests.saveAuthorizationRequest(request, start, new MockHttpServletResponse());
    }

    /** Le départ Google d'une ré-authentification, pour ce compte (AUTH-55, ADR 0034). */
    private void startGoogleReauthentication(UUID user) {
        MockHttpServletRequest start = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        start.setSession(session);
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://fournisseur.exemple/authorize")
                .clientId("client")
                .redirectUri("https://localhost:8443/login/oauth2/code/google")
                .state(STATE)
                .attributes(attributes -> {
                    attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "google");
                    attributes.put(OAuthAuthorizationRequests.REAUTHENTICATED_USER, user.toString());
                })
                .build();
        requests.saveAuthorizationRequest(request, start, new MockHttpServletResponse());
    }

    /** Session SEAL du compte, complète ou restreinte, chargée avant les filtres OAuth. */
    private static void signedIn(UUID user, String status) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        new SealPrincipal(new UserId(user)), null, List.of(new SimpleGrantedAuthority(status))));
    }

    private MockHttpServletRequest callback(String registrationId) {
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/" + registrationId);
        callback.setSession(session);
        callback.setParameter(OAuth2ParameterNames.STATE, STATE);
        return callback;
    }

    @Test
    @DisplayName("AUTH-30 : une session anonyme récupère la requête de son départ, retirée de la session")
    void auth30AnonymousSessionGetsItsRequest() {
        startGoogle();
        MockHttpServletRequest callback = callback("google");

        assertThat(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()))
                .isNotNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.REJECTION)).isNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.STARTED_WITH))
                .isEqualTo("google");
        assertThat(session.getAttribute(OAuthAuthorizationRequests.SESSION_ATTRIBUTE))
                .isNull();
    }

    @Test
    @DisplayName("AUTH-30 : une session ouverte ne récupère rien, même si la requête est restée ; elle est retirée")
    void auth30SignedInSessionGetsNothing() {
        startGoogle();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("compte", null, List.of()));
        MockHttpServletRequest callback = callback("google");

        assertThat(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()))
                .isNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.REJECTION)).isEqualTo("session_signed_in");
        assertThat(session.getAttribute(OAuthAuthorizationRequests.SESSION_ATTRIBUTE))
                .isNull();
    }

    @Test
    @DisplayName("AUTH-30, RFC 9700 : un retour sur l'adresse d'un autre fournisseur ne récupère rien (mix-up)")
    void auth30OtherProviderGetsNothing() {
        startGoogle();
        MockHttpServletRequest callback = callback("github");

        assertThat(requests.loadAuthorizationRequest(callback)).isNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.REJECTION)).isEqualTo("provider_mismatch");
    }

    @Test
    @DisplayName("ADR 0032, point 7 : forget retire la requête de la session, même sans state dans la requête")
    void adr0032ForgetRemovesTheRequest() {
        startGoogle();
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        login.setSession(session);

        requests.forget(login);

        assertThat(session.getAttribute(OAuthAuthorizationRequests.SESSION_ATTRIBUTE))
                .isNull();
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : la session complète du compte qui l'a demandée récupère sa ré-authentification")
    void auth55SameAccountGetsItsReauthentication() {
        UUID user = UUID.randomUUID();
        startGoogleReauthentication(user);
        signedIn(user, SealAuthorities.ACCOUNT_ACTIVE);
        MockHttpServletRequest callback = callback("google");

        assertThat(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()))
                .isNotNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.REJECTION)).isNull();
        assertThat(callback.getAttribute(OAuthAuthorizationRequests.REAUTHENTICATED_USER))
                .isEqualTo(user.toString());
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0034 : un autre compte, une session restreinte ou anonyme ne récupère pas la ré-authentification")
    void auth55OtherSessionsGetNothing() {
        UUID user = UUID.randomUUID();
        List<Runnable> others = List.of(
                () -> signedIn(UUID.randomUUID(), SealAuthorities.ACCOUNT_ACTIVE),
                () -> signedIn(user, SealAuthorities.ACCOUNT_PENDING_EMAIL),
                SecurityContextHolder::clearContext);
        for (Runnable other : others) {
            startGoogleReauthentication(user);
            other.run();
            MockHttpServletRequest callback = callback("google");

            assertThat(requests.removeAuthorizationRequest(callback, new MockHttpServletResponse()))
                    .isNull();
            assertThat(callback.getAttribute(OAuthAuthorizationRequests.REJECTION))
                    .isEqualTo("reauth_session_mismatch");
            SecurityContextHolder.clearContext();
        }
    }
}
