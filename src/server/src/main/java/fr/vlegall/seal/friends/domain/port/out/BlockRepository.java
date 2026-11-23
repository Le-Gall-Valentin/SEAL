package fr.vlegall.seal.friends.domain.port.out;

import fr.vlegall.seal.friends.domain.model.UserPair;

/** Table {@code blocks}, écrite par FRD-12 (P1). */
@FunctionalInterface
public interface BlockRepository {

    /** FRD-03 : vrai si l'un des deux a bloqué l'autre. */
    boolean blockedEitherWay(UserPair pair);
}
