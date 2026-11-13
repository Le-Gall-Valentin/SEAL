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
}
