package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Compte, tel que le contexte {@code identity} le connaît.
 *
 * @param email {@code null} seulement pour un compte supprimé
 */
public record Account(
        UserId id,
        Username username,
        DisplayName displayName,
        @Nullable EmailAddress email,
        AccountStatus status) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        if (email == null && status != AccountStatus.DELETED) {
            throw new IllegalArgumentException("seul un compte supprimé n'a pas de courriel");
        }
    }
}
