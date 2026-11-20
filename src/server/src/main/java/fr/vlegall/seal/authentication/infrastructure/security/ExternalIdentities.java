package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Section 5.4 du CDC : l'identité stable et le courriel de chaque fournisseur, lus strictement. Une valeur absente ou
 * mal typée refuse la connexion (échec fermé) ; seul un booléen {@code true} vaut un courriel vérifié.
 */
final class ExternalIdentities {

    private ExternalIdentities() {}

    /** @throws OAuth2AuthenticationException {@code invalid_user_info}, sans détail, pour toute identité inexploitable */
    static ExternalIdentity from(OAuthProvider provider, OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        try {
            return switch (provider) {
                case GOOGLE -> google(attributes);
                case GITHUB -> github(attributes);
                case MICROSOFT -> throw invalid();
            };
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    /** Google : {@code sub}, {@code email}, {@code email_verified}. */
    private static ExternalIdentity google(Map<String, Object> claims) {
        return ExternalIdentity.of(
                OAuthProvider.GOOGLE,
                text(claims, "sub"),
                text(claims, "email"),
                Boolean.TRUE.equals(claims.get("email_verified")),
                optionalText(claims, "name"));
    }

    /** GitHub : {@code id} numérique de {@code /user}, adresse principale de {@code /user/emails} (GitHubUserService). */
    private static ExternalIdentity github(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (!(id instanceof Integer || id instanceof Long) || ((Number) id).longValue() <= 0) {
            throw invalid();
        }
        String name = optionalText(attributes, "name");
        if (name == null || name.isBlank()) {
            name = optionalText(attributes, "login");
        }
        return ExternalIdentity.of(
                OAuthProvider.GITHUB,
                Long.toString(((Number) id).longValue()),
                text(attributes, GitHubUserService.PRIMARY_EMAIL),
                Boolean.TRUE.equals(attributes.get(GitHubUserService.PRIMARY_EMAIL_VERIFIED)),
                name);
    }

    private static String text(Map<String, Object> attributes, String name) {
        if (attributes.get(name) instanceof String value) {
            return value;
        }
        throw invalid();
    }

    private static @Nullable String optionalText(Map<String, Object> attributes, String name) {
        return attributes.get(name) instanceof String value ? value : null;
    }

    static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
    }
}
