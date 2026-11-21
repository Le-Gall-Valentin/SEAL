package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.DisplayName;

/** ACC-03 : nom affiché d'un compte actif, déjà conforme à la règle de {@link DisplayName}. */
public interface UpdateProfileUseCase {

    void changeDisplayName(UserId user, DisplayName displayName);
}
