package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.Vault;
import java.util.Optional;

/** Table {@code key_vaults} : un coffre par compte, versionné (KEY-32, ADR 0025 point 6). */
public interface VaultRepository {

    Optional<Vault> find(UserId user);

    /**
     * Écrit le coffre s'il suit la version enregistrée : version 1 sans coffre, sinon version enregistrée plus 1.
     * Faux sinon, même sous concurrence.
     */
    boolean save(UserId user, Vault vault);
}
