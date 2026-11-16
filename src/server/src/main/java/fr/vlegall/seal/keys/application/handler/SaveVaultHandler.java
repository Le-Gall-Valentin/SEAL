package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.SaveVaultUseCase;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class SaveVaultHandler implements SaveVaultUseCase {

    private final VaultRepository vaults;

    public SaveVaultHandler(VaultRepository vaults) {
        this.vaults = vaults;
    }

    /** ADR 0025, point 6 : mise à jour conditionnelle, la version écrite suit l'enregistrée de 1 exactement. */
    @Override
    @Transactional
    public boolean save(UserId user, Vault vault) {
        return vaults.save(user, vault);
    }
}
