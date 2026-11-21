package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;

/**
 * AUTH-32 : compte créé et lié au terme d'une inscription OAuth.
 *
 * @param active vrai si le fournisseur garantissait le courriel : la session est alors complète, restreinte sinon
 */
public record OAuthRegistration(UserId user, boolean active) {

    public OAuthRegistration {
        Objects.requireNonNull(user, "user");
    }
}
