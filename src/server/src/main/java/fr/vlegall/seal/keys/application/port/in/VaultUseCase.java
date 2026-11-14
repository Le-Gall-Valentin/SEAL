package fr.vlegall.seal.keys.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.Vault;
import java.util.Optional;

/** KEY-32 : coffre de clés du compte, lu et écrit avec contrôle de version optimiste (ADR 0025, point 6). */
public interface VaultUseCase {

    Optional<Vault> read(UserId user);

    /** Faux si {@code vault.version()} ne suit pas la version enregistrée : 409 {@code VAULT_VERSION_CONFLICT}. */
    boolean save(UserId user, Vault vault);
}
