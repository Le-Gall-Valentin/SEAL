package fr.vlegall.seal.authentication.domain.model;

import java.util.HashSet;
import java.util.List;

/**
 * Méthodes de connexion d'un compte (ADR 0035, point 2) : un mot de passe ou non, et les fournisseurs liés, configurés
 * ou non (ACC-04 doit pouvoir délier), dans l'ordre des boutons de la maquette. L'écran Paramètres en tire la
 * ré-authentification qu'il propose (AUTH-55).
 */
public record LoginMethods(boolean passwordSet, List<OAuthProvider> providers) {

    /** @throws IllegalArgumentException si un fournisseur est répété : un compte n'a qu'une liaison par fournisseur */
    public LoginMethods {
        if (new HashSet<>(providers).size() != providers.size()) {
            throw new IllegalArgumentException("fournisseur répété");
        }
        providers = List.copyOf(providers.stream().sorted().toList());
    }
}
