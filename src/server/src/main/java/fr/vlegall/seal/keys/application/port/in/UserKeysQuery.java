package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.UserKeys;
import java.util.Optional;

/**
 * KEY-33, API-02 : certificat courant et sous-clés valides de {@code owner}, lus par {@code caller} : un nouvel appareil
 * y compare son IK restaurée, un ami y prend de quoi chiffrer. Vide pour tout autre compte, comme sans certificat
 * courant (ADR 0029, point 4 ; ADR 0037, point 7) ; les membres d'une conversation commune s'y ajoutent à la PR 3.
 */
public interface UserKeysQuery {
    Optional<UserKeys> read(UserId caller, UserId owner);
}
