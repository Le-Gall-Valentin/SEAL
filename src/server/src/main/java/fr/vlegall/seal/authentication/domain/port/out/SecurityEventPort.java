package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;
import org.jspecify.annotations.Nullable;

/** Journal des connexions, déconnexions et réinitialisations (SEC-20), écrit hors de toute transaction (ADR 0021). */
public interface SecurityEventPort {

    void loginSucceeded(UserId user);

    /**
     * La saisie n'est jamais écrite : ce pourrait être un mot de passe tapé dans le champ identifiant (SEC-21).
     *
     * @param user compte que désigne la saisie, s'il existe
     */
    void loginFailed(@Nullable UserId user);

    void loggedOut(UserId user);

    /** AUTH-70 : mot de passe réinitialisé par lien. */
    void passwordReset(UserId user);
}
