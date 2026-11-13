package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.port.out.SessionRevocationPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SessionRevocation;
import org.springframework.stereotype.Component;

/** Révoque les sessions d'un compte par {@code security} (AUTH-50, AUTH-70). */
@Component
public class SessionRevocationAdapter implements SessionRevocationPort {

    private final SessionRevocation revocation;

    public SessionRevocationAdapter(SessionRevocation revocation) {
        this.revocation = revocation;
    }

    @Override
    public void revokeAll(UserId user) {
        revocation.revokeAll(user);
    }
}
