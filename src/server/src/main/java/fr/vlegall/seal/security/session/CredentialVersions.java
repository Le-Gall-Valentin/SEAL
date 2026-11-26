package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;
import java.util.OptionalLong;

/**
 * Version courante du mot de passe d'un compte (ADR 0023), fournie par le contexte {@code authentication} : {@code
 * security} n'en dépend pas (ADR 0008, règle 8).
 */
@FunctionalInterface
public interface CredentialVersions {

    /** Version du mot de passe du compte ; vide s'il n'en a pas. */
    OptionalLong current(UserId user);

    /**
     * ADR 0023 : la session de ce principal est-elle encore valable ? Une session sans version gravée (OAuth) l'est
     * toujours ; sinon sa version doit être la courante.
     */
    default boolean isCurrent(SealPrincipal principal) {
        Long engraved = principal.credentialVersion();
        if (engraved == null) {
            return true;
        }
        OptionalLong current = current(principal.userId());
        return current.isPresent() && current.getAsLong() == engraved;
    }
}
