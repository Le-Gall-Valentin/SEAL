package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.UserKeys;
import java.util.Optional;

/**
 * KEY-33 : certificat courant et sous-clés valides de {@code owner}, lus par {@code caller} ; un nouvel appareil y
 * compare son IK restaurée. Réservé à l'appelant lui-même jusqu'au lot 3 (API-02, ADR 0029, point 4) : vide pour un
 * autre compte, comme sans certificat courant.
 */
public interface UserKeysQuery {
    Optional<UserKeys> read(UserId caller, UserId owner);
}
