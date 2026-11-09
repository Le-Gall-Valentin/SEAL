package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;
import org.jspecify.annotations.Nullable;

/** Journal des connexions et déconnexions (SEC-20), écrit hors de toute transaction (ADR 0021, point 7). */
public interface SecurityEventPort {

    void loginSucceeded(UserId user);

    /**
     * @param login saisie, filtrée avant d'être écrite (SEC-21)
     * @param user compte que désigne la saisie, s'il existe
     */
    void loginFailed(String login, @Nullable UserId user);

    void loggedOut(UserId user);
}
