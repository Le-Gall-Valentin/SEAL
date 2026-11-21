package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.OAuthProvidersQuery;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.domain.port.out.ConfiguredProviders;
import fr.vlegall.seal.common.ApplicationService;
import java.util.List;

/** AUTH-35, OPS-03 : sans base de données, donc sans transaction. */
@ApplicationService
public class OAuthProvidersHandler implements OAuthProvidersQuery {

    private final ConfiguredProviders providers;

    public OAuthProvidersHandler(ConfiguredProviders providers) {
        this.providers = providers;
    }

    @Override
    public List<OAuthProvider> configured() {
        return providers.configured();
    }
}
