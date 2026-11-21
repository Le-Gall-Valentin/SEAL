package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.List;

/** AUTH-35, OPS-03 : fournisseurs OAuth proposés à la connexion ({@code GET /api/v1/auth/providers}). */
public interface OAuthProvidersQuery {

    List<OAuthProvider> configured();
}
