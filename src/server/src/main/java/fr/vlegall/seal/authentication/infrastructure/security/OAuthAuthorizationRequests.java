package fr.vlegall.seal.authentication.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

/**
 * AUTH-30, ADR 0032 (point 7) : requêtes d'autorisation gardées dans la session, comme le fait Spring, mais rendues au
 * retour seulement à une session anonyme et pour le fournisseur du départ (défense contre le mix-up, RFC 9700). Le
 * filtre du retour passe avant les règles d'accès : c'est ici que le retour d'une session ouverte est refusé. La cause
 * d'un refus et le fournisseur du départ sont laissés dans la requête pour le journal.
 */
@Component
public final class OAuthAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Cause d'un retour refusé ici ({@code session_signed_in}, {@code provider_mismatch}), en attribut de requête. */
    static final String REJECTION = OAuthAuthorizationRequests.class.getName() + ".REJECTION";

    /** {@code registrationId} du départ dont le retour arrive, en attribut de requête. */
    static final String STARTED_WITH = OAuthAuthorizationRequests.class.getName() + ".STARTED_WITH";

    /** Attribut de session où Spring range la requête d'autorisation. */
    static final String SESSION_ATTRIBUTE =
            HttpSessionOAuth2AuthorizationRequestRepository.class.getName() + ".AUTHORIZATION_REQUEST";

    private final HttpSessionOAuth2AuthorizationRequestRepository sessions =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public @Nullable OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return accepted(request, sessions.loadAuthorizationRequest(request));
    }

    @Override
    public void saveAuthorizationRequest(
            @Nullable OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        sessions.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    /** La requête est toujours retirée de la session : un retour refusé ne peut pas être rejoué. */
    @Override
    public @Nullable OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        return accepted(request, sessions.removeAuthorizationRequest(request, response));
    }

    /** Oublie la requête d'autorisation de la session, quelle qu'elle soit (ouverture d'une session SEAL). */
    void forget(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    private @Nullable OAuth2AuthorizationRequest accepted(
            HttpServletRequest request, @Nullable OAuth2AuthorizationRequest stored) {
        if (stored == null) {
            return null;
        }
        String startedWith = stored.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        request.setAttribute(STARTED_WITH, startedWith);
        if (OAuthRequests.signedIn()) {
            request.setAttribute(REJECTION, "session_signed_in");
            return null;
        }
        String callbackProvider = OAuthRequests.callbackRegistrationId(request);
        if (callbackProvider != null && !Objects.equals(callbackProvider, startedWith)) {
            request.setAttribute(REJECTION, "provider_mismatch");
            return null;
        }
        return stored;
    }
}
