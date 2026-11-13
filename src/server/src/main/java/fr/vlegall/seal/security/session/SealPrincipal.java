package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Utilisateur d'une session. Son nom est l'UUID du compte : Spring Session JDBC l'indexe dans
 * {@code PRINCIPAL_NAME}, pour lister et révoquer les sessions d'un utilisateur (AUTH-50, AUTH-54).
 *
 * @param credentialVersion version du mot de passe avec lequel la session a été ouverte, comparée à la base à chaque
 *     requête par {@link CredentialVersionFilter} ; absente pour une session ouverte sans mot de passe (inscription,
 *     vérification), qui n'est pas comparée (ADR 0023)
 */
public record SealPrincipal(UserId userId, @Nullable Long credentialVersion) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    public SealPrincipal {
        Objects.requireNonNull(userId, "userId");
    }

    /** Session ouverte sans mot de passe : aucune version à comparer. */
    public SealPrincipal(UserId userId) {
        this(userId, null);
    }

    @Override
    public String getName() {
        return userId.value().toString();
    }
}
