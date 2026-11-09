package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.identity.domain.model.UsernameAvailability;

/** AUTH-05 : disponibilité d'un identifiant et trois suggestions. */
public interface UsernameAvailabilityQuery {

    UsernameAvailability check(String candidate);
}
