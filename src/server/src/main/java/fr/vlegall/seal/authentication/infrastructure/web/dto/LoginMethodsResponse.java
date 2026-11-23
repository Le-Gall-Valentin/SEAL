package fr.vlegall.seal.authentication.infrastructure.web.dto;

import fr.vlegall.seal.authentication.domain.model.LoginMethods;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.List;

/**
 * ADR 0035, point 2 : réponse de {@code GET /api/v1/account/identities}. ACC-04 ajoutera à chaque fournisseur son
 * courriel et sa date de liaison, sans changer ces champs.
 */
public record LoginMethodsResponse(boolean passwordSet, List<LinkedProvider> providers) {

    public LoginMethodsResponse {
        providers = List.copyOf(providers);
    }

    /** Un fournisseur lié, par son {@code registrationId} (AUTH-35). */
    public record LinkedProvider(String provider) {

        static LinkedProvider of(OAuthProvider provider) {
            return new LinkedProvider(provider.registrationId());
        }
    }

    public static LoginMethodsResponse of(LoginMethods methods) {
        return new LoginMethodsResponse(
                methods.passwordSet(),
                methods.providers().stream().map(LinkedProvider::of).toList());
    }
}
