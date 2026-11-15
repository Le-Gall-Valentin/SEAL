package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.Vault;
import java.util.Optional;

/** KEY-33 : coffre de clés du compte, relu par un nouvel appareil ; vide s'il n'en a pas encore. */
public interface VaultQuery {

    Optional<Vault> read(UserId user);
}
