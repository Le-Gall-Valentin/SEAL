package fr.vlegall.seal.authentication.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Fournisseurs d'identité de la section 5.4 du CDC, dans l'ordre des boutons de la maquette. Le nom de chacun est celui
 * de la colonne {@code oauth_identities.provider} ; son {@code registrationId} est celui des adresses OAuth (AUTH-35).
 */
public enum OAuthProvider {
    MICROSOFT("microsoft"),
    GOOGLE("google"),
    GITHUB("github");

    private final String registrationId;

    OAuthProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String registrationId() {
        return registrationId;
    }

    /** Le fournisseur d'un {@code registrationId}, à la casse exacte ; vide pour tout autre texte. */
    public static Optional<OAuthProvider> fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(registrationId))
                .findFirst();
    }
}
