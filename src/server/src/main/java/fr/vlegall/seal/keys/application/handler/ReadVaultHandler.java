package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.VaultQuery;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ReadVaultHandler implements VaultQuery {

    private final VaultRepository vaults;

    public ReadVaultHandler(VaultRepository vaults) {
        this.vaults = vaults;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vault> read(UserId user) {
        return vaults.find(user);
    }
}
