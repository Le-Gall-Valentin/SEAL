package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.EmailAddress;

/** Journal d'imputabilité (SEC-20), écrit dans la transaction du geste ; le courriel y est masqué (SEC-21). */
public interface SecurityEventPort {

    void accountRegistered(UserId user, EmailAddress email);

    /** AUTH-17. */
    void emailChanged(UserId user, EmailAddress newEmail);
}
