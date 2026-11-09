package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.LogoutUseCase;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class LogoutHandler implements LogoutUseCase {

    private final SecurityEventPort journal;

    public LogoutHandler(SecurityEventPort journal) {
        this.journal = journal;
    }

    /** {@code NEVER} : l'événement s'écrit dans sa propre transaction (ADR 0021, point 7). */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void logout(UserId user) {
        journal.loggedOut(user);
    }
}
