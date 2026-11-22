package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;

/** ADR 0034 : invalide les liens de réinitialisation vivants d'un compte, dans la transaction de l'appelant. */
public interface InvalidateResetLinksUseCase {

    void invalidate(UserId user);
}
