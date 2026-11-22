package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;

/**
 * ADR 0034 : invalide les liens de réinitialisation vivants d'un compte, qu'{@code authentication} possède, dans la
 * transaction de l'appelant. Un lien envoyé à l'ancienne adresse ne doit plus servir.
 */
@FunctionalInterface
public interface PasswordResetLinksPort {

    void invalidate(UserId user);
}
