package fr.vlegall.seal.authentication.domain.model;

import java.util.List;

/**
 * Méthodes de connexion d'un compte (ADR 0035, point 2) : un mot de passe ou non, et les fournisseurs liés, dans
 * l'ordre des boutons de la maquette. L'écran Paramètres en tire la ré-authentification qu'il propose (AUTH-55).
 */
public record LoginMethods(boolean passwordSet, List<OAuthProvider> providers) {

    public LoginMethods {
        providers = List.copyOf(providers);
    }
}
