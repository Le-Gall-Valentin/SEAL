package fr.vlegall.seal.authentication.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Jetons du fournisseur jamais gardés (CDC 5.4, modèle BFF) : SEAL ne s'en sert que le temps du retour OAuth, pour lire
 * l'identité. Par défaut, Spring les rangerait dans la session, donc en base avec Spring Session JDBC.
 */
final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            String clientRegistrationId, @Nullable Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Rien à garder.
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            @Nullable Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Rien n'a été gardé.
    }
}
