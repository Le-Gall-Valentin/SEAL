package fr.vlegall.seal.identity.infrastructure.authentication;

import fr.vlegall.seal.authentication.application.port.in.InvalidateResetLinksUseCase;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.port.out.PasswordResetLinksPort;
import org.springframework.stereotype.Component;

/** Confie l'invalidation des liens au contexte {@code authentication}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class PasswordResetLinksAdapter implements PasswordResetLinksPort {

    private final InvalidateResetLinksUseCase resetLinks;

    public PasswordResetLinksAdapter(InvalidateResetLinksUseCase resetLinks) {
        this.resetLinks = resetLinks;
    }

    @Override
    public void invalidate(UserId user) {
        resetLinks.invalidate(user);
    }
}
