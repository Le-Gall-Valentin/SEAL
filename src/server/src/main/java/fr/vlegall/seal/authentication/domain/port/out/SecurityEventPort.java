package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import org.jspecify.annotations.Nullable;

/** Journal des connexions et déconnexions (SEC-20), écrit hors de toute transaction (ADR 0021, point 7). */
public interface SecurityEventPort {

    void loginSucceeded(UserId user);

    /**
     * La saisie n'est jamais écrite : ce pourrait être un mot de passe tapé dans le champ identifiant (SEC-21).
     *
     * @param user compte que désigne la saisie, s'il existe
     */
    void loginFailed(@Nullable UserId user);

    void loggedOut(UserId user);

    /** SEC-20 : compte lié à un fournisseur OAuth (AUTH-31), dans la transaction qui crée la liaison. */
    void oauthLinked(UserId user, OAuthProvider provider);

    /** SEC-20 : connexion par un fournisseur OAuth, nommé dans le détail. */
    void oauthLoginSucceeded(UserId user, OAuthProvider provider);

    /**
     * SEC-20, SEC-21 : retour OAuth refusé. Ni courriel, ni code, ni jeton : le fournisseur, s'il est connu, et une cause
     * courte ({@code email_in_use}, {@code account_unusable} ou le code d'erreur OAuth).
     *
     * @param user compte lié à l'identité, s'il existe
     */
    void oauthLoginFailed(@Nullable UserId user, @Nullable OAuthProvider provider, String cause);
}
