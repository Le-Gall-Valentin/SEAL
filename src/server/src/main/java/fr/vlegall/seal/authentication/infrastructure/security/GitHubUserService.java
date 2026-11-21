package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Utilisateur GitHub (section 5.4 du CDC) : {@code id} de {@code /user}, puis l'adresse principale de
 * {@code /user/emails} et sa vérification. GitHub n'a pas d'OIDC : sans ce second appel, SEAL ne saurait pas si le
 * courriel est vérifié. L'identité est contrôlée dès ici, pour qu'un refus passe par l'échec du retour OAuth.
 */
final class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /** Adresse principale, ajoutée aux attributs de {@code /user}. */
    static final String PRIMARY_EMAIL = "seal_primary_email";

    /** Vérification de l'adresse principale, en booléen. */
    static final String PRIMARY_EMAIL_VERIFIED = "seal_primary_email_verified";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAILS =
            new ParameterizedTypeReference<>() {};

    private final DefaultOAuth2UserService users = new DefaultOAuth2UserService();
    private final RestClient rest =
            RestClient.builder().requestFactory(ProviderHttp.requestFactory()).build();

    GitHubUserService() {
        users.setRestOperations(ProviderHttp.restTemplate());
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        ClientRegistration registration = request.getClientRegistration();
        if (!OAuthProvider.GITHUB.registrationId().equals(registration.getRegistrationId())) {
            throw ExternalIdentities.invalid();
        }
        OAuth2User user = users.loadUser(request);
        if (user == null) {
            throw ExternalIdentities.invalid();
        }
        Map<String, Object> primary = primaryEmail(request);
        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
        if (primary.get("email") instanceof String email) {
            attributes.put(PRIMARY_EMAIL, email);
        }
        attributes.put(PRIMARY_EMAIL_VERIFIED, Boolean.TRUE.equals(primary.get("verified")));
        OAuth2User enriched = new DefaultOAuth2User(
                user.getAuthorities(),
                attributes,
                registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
        ExternalIdentities.from(OAuthProvider.GITHUB, enriched);
        return enriched;
    }

    /** L'adresse marquée {@code primary} ; toute autre réponse refuse la connexion. */
    private Map<String, Object> primaryEmail(OAuth2UserRequest request) {
        String uri = request.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUri()
                + "/emails";
        List<Map<String, Object>> emails;
        try {
            emails = rest.get()
                    .uri(uri)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + request.getAccessToken().getTokenValue())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(EMAILS);
        } catch (RestClientException e) {
            throw ExternalIdentities.invalid();
        }
        if (emails == null) {
            throw ExternalIdentities.invalid();
        }
        return emails.stream()
                .filter(email -> Boolean.TRUE.equals(email.get("primary")))
                .findFirst()
                .orElseThrow(ExternalIdentities::invalid);
    }
}
