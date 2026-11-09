package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * Compte d'une connexion, demandé par le contexte {@code authentication} (AUTH-20, ADR 0008). Appelé depuis un autre
 * contexte : la signature ne porte que des types de la JDK et {@link UserId}.
 */
public interface AccountLookupQuery {

    /**
     * Compte qui correspond à la saisie, normalisée par {@code Unicode.foldLogin} : identifiant d'un compte non supprimé,
     * ou courriel d'un compte actif (ADR 0019). Vide pour toute autre saisie, sans erreur.
     */
    Optional<UserId> findForLogin(String login);

    /** Vrai si le compte est actif, donc son courriel vérifié (AUTH-16). */
    boolean isVerified(UserId user);
}
