package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;

/** AUTH-14 : renvoi du code ; le précédent cesse de valoir. */
public interface ResendCodeUseCase {

    void resend(UserId user);
}
