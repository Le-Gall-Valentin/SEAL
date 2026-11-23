package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Annuaire interne demandé par le contexte {@code friends} (FRD-02, ADR 0037, point 8) : il ne trouve que les comptes
 * actifs, par leur identifiant exact, et ne livre ni courriel ni statut. Aucune recherche ni liste d'utilisateurs n'en
 * sort vers l'API (API-01). Appelé depuis un autre contexte : la signature ne porte que des types de la JDK,
 * {@link UserId} et l'interface {@link Profile}, rangée ici même ; son implémentation reste dans {@code identity}.
 */
public interface UserDirectoryQuery {

    /** Profil public d'un compte actif. */
    interface Profile {
        UserId user();

        /** Identifiant, sans {@code @}. */
        String username();

        String displayName();
    }

    /**
     * Profil du compte actif qui porte exactement cet identifiant (AUTH-01, déjà normalisé par l'appelant) ; vide pour un
     * compte en attente, supprimé ou inconnu, et pour un identifiant mal formé, sans erreur.
     */
    Optional<Profile> findActiveByUsername(String username);

    /** FRD-10 : profils des comptes encore actifs parmi ceux demandés, dans l'ordre de la demande, sans doublon. */
    List<Profile> activeProfiles(Collection<UserId> users);

    /** FRD-03 : faux si le compte a fermé ses invitations (politique {@code NOBODY}, ACC-06). */
    boolean acceptsInvitations(UserId user);
}
