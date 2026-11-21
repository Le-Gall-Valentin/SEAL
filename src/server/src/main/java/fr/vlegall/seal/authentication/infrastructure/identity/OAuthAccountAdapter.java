package fr.vlegall.seal.authentication.infrastructure.identity;

import fr.vlegall.seal.authentication.domain.port.out.OAuthAccountPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CreateOAuthAccountUseCase;
import org.springframework.stereotype.Component;

/** Demande la création du compte au contexte {@code identity}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class OAuthAccountAdapter implements OAuthAccountPort {

    private final CreateOAuthAccountUseCase accounts;

    public OAuthAccountAdapter(CreateOAuthAccountUseCase accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserId create(String username, String displayName, String email, boolean emailVerified) {
        return accounts.create(username, displayName, email, emailVerified);
    }
}
