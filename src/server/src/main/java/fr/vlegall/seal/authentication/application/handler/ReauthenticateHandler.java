package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.ReauthenticateUseCase;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-55, ADR 0034 : le mot de passe d'une session complète est vérifié de nouveau ({@link CurrentPasswordCheck}). */
@ApplicationService
public class ReauthenticateHandler implements ReauthenticateUseCase {

    private final CurrentPasswordCheck check;

    public ReauthenticateHandler(CurrentPasswordCheck check) {
        this.check = check;
    }

    /** {@code NEVER} : Argon2id ne retient aucune connexion (ADR 0020). */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public boolean reauthenticate(UserId user, String password) {
        return check.verify(user, password).isPresent();
    }
}
