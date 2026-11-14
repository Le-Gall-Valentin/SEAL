package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.VaultUseCase;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class VaultHandler implements VaultUseCase {

    private final VaultRepository vaults;

    public VaultHandler(VaultRepository vaults) {
        this.vaults = vaults;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vault> read(UserId user) {
        return vaults.find(user);
    }

    @Override
    @Transactional
    public boolean save(UserId user, Vault vault) {
        return vaults.save(user, vault);
    }
}
