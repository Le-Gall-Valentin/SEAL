package fr.vlegall.seal.authentication.infrastructure.web.dto;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.List;

/** AUTH-35 : {@code registrationId} des fournisseurs configurés, dans l'ordre des boutons de la maquette. */
public record ProvidersResponse(List<String> providers) {

    public ProvidersResponse {
        providers = List.copyOf(providers);
    }

    public static ProvidersResponse of(List<OAuthProvider> configured) {
        return new ProvidersResponse(
                configured.stream().map(OAuthProvider::registrationId).toList());
    }
}
