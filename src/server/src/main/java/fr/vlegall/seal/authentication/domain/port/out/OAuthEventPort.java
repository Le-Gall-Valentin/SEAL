package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import org.jspecify.annotations.Nullable;

/** Journal des connexions OAuth (SEC-20), écrit hors de toute transaction, comme celui des connexions (ADR 0021). */
public interface OAuthEventPort {

    /** SEC-20 : connexion par un fournisseur OAuth, nommé dans le détail. */
    void oauthLoginSucceeded(UserId user, OAuthProvider provider);

    /**
     * SEC-20, SEC-21 : retour OAuth refusé. Ni courriel, ni code, ni jeton : le fournisseur, s'il est connu, et une cause
     * courte : {@code email_in_use}, {@code account_unusable}, {@code session_signed_in}, {@code provider_mismatch},
     * {@code invalid_user_info}, un code d'erreur OAuth connu, ou {@code oauth_error} pour tout autre.
     *
     * @param user compte lié à l'identité, s'il existe
     */
    void oauthLoginFailed(@Nullable UserId user, @Nullable OAuthProvider provider, String cause);
}
