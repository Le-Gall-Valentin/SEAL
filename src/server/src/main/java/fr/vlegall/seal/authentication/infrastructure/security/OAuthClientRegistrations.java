package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.ConfiguredProviders;
import fr.vlegall.seal.common.OAuthClientProperties;
import fr.vlegall.seal.common.OAuthClientProperties.Client;
import fr.vlegall.seal.security.web.OAuthRoutes;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * AUTH-30, AUTH-35, OPS-03 : applications OAuth configurées, en clients confidentiels avec PKCE S256. Contrairement à
 * l'{@code InMemoryClientRegistrationRepository} de Spring, ce dépôt accepte de n'en avoir aucune : SEAL démarre alors
 * sans fournisseur (OPS-03).
 */
public final class OAuthClientRegistrations implements ClientRegistrationRepository, ConfiguredProviders {

    private final Map<OAuthProvider, ClientRegistration> byProvider = new EnumMap<>(OAuthProvider.class);

    /**
     * Refuse une inscription d'un fournisseur inconnu, en double ou sans PKCE : Spring Security n'applique PKCE à un
     * client confidentiel que si {@code requireProofKey} est vrai (AUTH-30).
     */
    public OAuthClientRegistrations(List<ClientRegistration> registrations) {
        for (ClientRegistration registration : registrations) {
            OAuthProvider provider = OAuthProvider.fromRegistrationId(registration.getRegistrationId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "fournisseur OAuth inconnu : " + registration.getRegistrationId()));
            if (!registration.getClientSettings().isRequireProofKey()) {
                throw new IllegalArgumentException("PKCE est obligatoire (AUTH-30) : " + provider.registrationId());
            }
            if (byProvider.putIfAbsent(provider, registration) != null) {
                throw new IllegalArgumentException("fournisseur OAuth en double : " + provider.registrationId());
            }
        }
    }

    /** Les applications configurées par {@code seal.oauth} (ADR 0032, point 5). */
    public static OAuthClientRegistrations from(OAuthClientProperties properties, URI origin) {
        List<ClientRegistration> registrations = new ArrayList<>();
        properties
                .google()
                .ifPresent(client -> registrations.add(
                        registration(OAuthProvider.GOOGLE, client, origin).build()));
        properties
                .github()
                .ifPresent(client -> registrations.add(
                        registration(OAuthProvider.GITHUB, client, origin).build()));
        return new OAuthClientRegistrations(registrations);
    }

    /**
     * Inscription d'un fournisseur de la section 5.4 : points d'accès de Spring Security, portées du CDC, PKCE exigé.
     * La redirection part de l'origine de SEAL, jamais de l'en-tête {@code Host} de la requête (AUTH-35).
     */
    public static ClientRegistration.Builder registration(OAuthProvider provider, Client client, URI origin) {
        CommonOAuth2Provider common = switch (provider) {
            case GOOGLE -> CommonOAuth2Provider.GOOGLE;
            case GITHUB -> CommonOAuth2Provider.GITHUB;
            case MICROSOFT -> throw new IllegalArgumentException("Microsoft arrive avec le P1 (AUTH-34)");
        };
        return common.getBuilder(provider.registrationId())
                .clientId(client.clientId())
                .clientSecret(client.clientSecret())
                .scope(scopes(provider))
                .redirectUri(origin + OAuthRoutes.CALLBACK_BASE + "/" + provider.registrationId())
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(true)
                        .build());
    }

    /** Section 5.4 du CDC. */
    private static List<String> scopes(OAuthProvider provider) {
        return provider == OAuthProvider.GITHUB
                ? List.of("read:user", "user:email")
                : List.of("openid", "email", "profile");
    }

    /** {@code null} pour un fournisseur inconnu ou non configuré, comme le veut le contrat de Spring. */
    @Override
    public @Nullable ClientRegistration findByRegistrationId(String registrationId) {
        return OAuthProvider.fromRegistrationId(registrationId)
                .map(byProvider::get)
                .orElse(null);
    }

    @Override
    public List<OAuthProvider> configured() {
        return byProvider.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
