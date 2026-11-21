package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import java.util.List;

/** AUTH-35, OPS-03 : fournisseurs dont l'application OAuth est configurée. */
public interface ConfiguredProviders {

    /** Dans l'ordre des boutons de la maquette ; vide si aucune application n'est configurée. */
    List<OAuthProvider> configured();
}
