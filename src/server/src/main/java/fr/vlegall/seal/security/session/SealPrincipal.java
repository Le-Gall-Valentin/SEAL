package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

/**
 * Utilisateur d'une session. Son nom est l'UUID du compte : Spring Session JDBC l'indexe dans
 * {@code PRINCIPAL_NAME}, pour lister et révoquer les sessions d'un utilisateur (AUTH-50, AUTH-54).
 */
public record SealPrincipal(UserId userId) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    public SealPrincipal {
        Objects.requireNonNull(userId, "userId");
    }

    @Override
    public String getName() {
        return userId.value().toString();
    }
}
