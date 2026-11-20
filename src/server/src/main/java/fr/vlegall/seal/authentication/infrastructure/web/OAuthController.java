package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.OAuthProvidersQuery;
import fr.vlegall.seal.authentication.infrastructure.web.dto.ProvidersResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion OAuth vue de l'API (section 14.1 du CDC). Le départ et le retour sont les filtres de Spring Security ; les
 * règles d'accès sont dans {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class OAuthController {

    private final OAuthProvidersQuery providers;

    public OAuthController(OAuthProvidersQuery providers) {
        this.providers = providers;
    }

    /** AUTH-35, OPS-03 : fournisseurs configurés, ouverts à tous ; le client masque les autres boutons. */
    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return ProvidersResponse.of(providers.configured());
    }
}
