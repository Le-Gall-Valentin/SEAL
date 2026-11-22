package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.UpdateProfileUseCase;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACC-03. La règle du nom (1 à 50 caractères, NFC, sans caractère de contrôle ni de direction) est portée par
 * {@link DisplayName}. SEC-20 ne range pas ce changement parmi les événements journalisés.
 */
@ApplicationService
public class UpdateProfileHandler implements UpdateProfileUseCase {

    private final UserRepository users;

    public UpdateProfileHandler(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void changeDisplayName(UserId user, DisplayName displayName) {
        AccountLocks.lockActive(users, user);
        users.changeDisplayName(user, displayName);
    }
}
