package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Compte d'une connexion ou d'une réinitialisation, demandé par le contexte {@code authentication} (AUTH-20, AUTH-70,
 * ADR 0008). Appelé depuis un autre contexte : la signature ne porte que des types de la JDK, {@link UserId} et
 * l'interface {@link Contact}, rangée ici même ; son implémentation reste dans {@code identity}.
 */
public interface AccountLookupQuery {

    /** Coordonnées d'un compte actif : de quoi lui écrire et appliquer AUTH-11 à son mot de passe (ADR 0022). */
    interface Contact {

        UserId user();

        /** Identifiant, sans {@code @}. */
        String username();

        /** Courriel vérifié. */
        String email();

        /** AUTH-11 : identifiant et partie locale du courriel, avant {@code +} et {@code @}, qui ne comptent pas. */
        List<String> personalWords();
    }

    /**
     * Compte qui correspond à la saisie, normalisée par {@code Unicode.foldLogin} : identifiant d'un compte non supprimé,
     * ou courriel d'un compte actif (ADR 0019). Vide pour toute autre saisie, sans erreur.
     */
    Optional<UserId> findForLogin(String login);

    /** Vrai si le compte est actif, donc son courriel vérifié (AUTH-16). */
    boolean isVerified(UserId user);

    /**
     * AUTH-70 : coordonnées du compte actif dont le courriel vérifié est la saisie, normalisée par
     * {@code Unicode.foldLogin}. Vide pour un compte en attente, supprimé ou inconnu, et pour toute saisie qui n'est pas
     * un courriel, sans erreur (ADR 0022, point 3).
     */
    Optional<Contact> findActiveByEmail(String email);

    /** AUTH-70 : coordonnées du compte s'il est encore actif, vide sinon. */
    Optional<Contact> findActiveContact(UserId user);
}
