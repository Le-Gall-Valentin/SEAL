package fr.vlegall.seal.keys.infrastructure.security;

import fr.vlegall.seal.keys.domain.port.out.SudoModePort;
import fr.vlegall.seal.security.session.SudoMode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** AUTH-55, KEY-34 : mode sudo de la session de la requête en cours, jugé par {@code security} (ADR 0034). */
@Component
public class KeysSudoModeAdapter implements SudoModePort {

    private final SudoMode sudo;

    public KeysSudoModeAdapter(SudoMode sudo) {
        this.sudo = sudo;
    }

    @Override
    public void require() {
        sudo.require(SecurityContextHolder.getContext().getAuthentication());
    }
}
