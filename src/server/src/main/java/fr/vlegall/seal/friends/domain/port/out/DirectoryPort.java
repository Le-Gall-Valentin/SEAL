package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Person;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Comptes actifs, demandés au contexte {@code identity} (ADR 0037, point 8). */
public interface DirectoryPort {

    /** FRD-02 : compte actif qui porte exactement cet identifiant, déjà normalisé. */
    Optional<Person> findActiveByUsername(String username);

    /** FRD-10 : profils des comptes encore actifs parmi ceux demandés, dans l'ordre de la demande. */
    List<Person> activeProfiles(Collection<UserId> users);

    /** FRD-03 : faux si le compte a fermé ses invitations. */
    boolean acceptsInvitations(UserId user);
}
