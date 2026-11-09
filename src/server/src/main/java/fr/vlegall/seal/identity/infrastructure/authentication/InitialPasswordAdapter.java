package fr.vlegall.seal.identity.infrastructure.authentication;

import fr.vlegall.seal.authentication.application.port.in.InitialPasswordUseCase;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.PreparedPassword;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Confie le mot de passe au contexte {@code authentication}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class InitialPasswordAdapter implements InitialPasswordPort {

    private final InitialPasswordUseCase initialPassword;

    public InitialPasswordAdapter(InitialPasswordUseCase initialPassword) {
        this.initialPassword = initialPassword;
    }

    @Override
    public Optional<PreparedPassword> prepare(String password, List<String> personalWords) {
        return initialPassword.prepare(password, personalWords).map(PreparedPassword::new);
    }

    @Override
    public void store(UserId user, PreparedPassword password) {
        initialPassword.store(user, password.hash());
    }
}
