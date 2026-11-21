package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;

/** Journal de la liaison OAuth (SEC-20), écrit dans la transaction qui crée la liaison (ADR 0013, point 6). */
public interface OAuthLinkEventPort {

    /** SEC-20 : compte lié à un fournisseur OAuth (AUTH-31). */
    void oauthLinked(UserId user, OAuthProvider provider);
}
