package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.InitialPasswordUseCase;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class InitialPasswordHandler implements InitialPasswordUseCase {

    private final PasswordPreparation preparation;
    private final PasswordHasher hasher;
    private final CredentialRepository credentials;

    public InitialPasswordHandler(
            PasswordPreparation preparation, PasswordHasher hasher, CredentialRepository credentials) {
        this.preparation = preparation;
        this.hasher = hasher;
        this.credentials = credentials;
    }

    /**
     * {@code NEVER} : un appel dans une transaction échoue au lieu de garder une connexion pendant le hachage. La portée
     * que {@code NEVER} ouvre n'est pas synchronisée (ADR 0020) : elle ne lie aucune connexion au fil.
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Optional<String> prepare(String password, List<String> personalWords) {
        return preparation.prepare(password, personalWords);
    }

    /**
     * {@code MANDATORY} : le mot de passe naît avec le compte, dans la même transaction, ou pas du tout. Seul un haché
     * que le hacheur reconnaît est écrit : un texte en clair passé par erreur est refusé (échec fermé, AUTH-13).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void store(UserId user, String preparedHash) {
        if (!hasher.recognises(preparedHash)) {
            throw new IllegalArgumentException("haché de mot de passe non reconnu");
        }
        credentials.save(user, preparedHash);
    }
}
