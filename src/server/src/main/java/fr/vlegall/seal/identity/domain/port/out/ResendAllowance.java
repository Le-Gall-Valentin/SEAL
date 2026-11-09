package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** Renvois de code restants dans l'heure (AUTH-14, 15.2), partagés par le renvoi et le changement d'adresse. */
@FunctionalInterface
public interface ResendAllowance {

    long remaining(UserId user);
}
