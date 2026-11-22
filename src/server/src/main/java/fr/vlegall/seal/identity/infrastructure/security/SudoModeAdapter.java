package fr.vlegall.seal.identity.infrastructure.security;

import fr.vlegall.seal.identity.domain.port.out.SudoModePort;
import fr.vlegall.seal.security.session.SudoMode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** AUTH-55 : mode sudo de la session de la requête en cours, jugé par {@code security} (ADR 0034). */
@Component
public class SudoModeAdapter implements SudoModePort {

    private final SudoMode sudo;

    public SudoModeAdapter(SudoMode sudo) {
        this.sudo = sudo;
    }

    @Override
    public void require() {
        sudo.require(SecurityContextHolder.getContext().getAuthentication());
    }
}
