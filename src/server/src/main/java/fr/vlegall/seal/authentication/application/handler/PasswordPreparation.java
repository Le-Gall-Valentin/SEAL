package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.PasswordPolicy;
import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.ApplicationService;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Politique AUTH-11 puis hachage Argon2id, pour l'inscription comme pour la réinitialisation. */
@ApplicationService
public class PasswordPreparation {

    private final Blocklist blocklist;
    private final PasswordHasher hasher;

    public PasswordPreparation(Blocklist blocklist, PasswordHasher hasher) {
        this.blocklist = blocklist;
        this.hasher = hasher;
    }

    /**
     * {@code NEVER} : un appel dans une transaction échoue au lieu de garder une connexion pendant le hachage (ADR 0019
     * point 3, ADR 0020).
     *
     * @param personalWords identifiant et partie locale du courriel, qui ne comptent pas (ADR 0019)
     * @return le haché, préfixé par son algorithme ; vide si le mot de passe enfreint AUTH-11
     */
    @Transactional(propagation = Propagation.NEVER)
    public Optional<String> prepare(String password, List<String> personalWords) {
        Password candidate = Password.fromInput(password);
        if (!PasswordPolicy.accepts(candidate, personalWords, blocklist)) {
            return Optional.empty();
        }
        return Optional.of(hasher.hash(candidate));
    }
}
