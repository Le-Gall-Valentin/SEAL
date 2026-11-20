package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.common.UserId;

/** AUTH-32 : compte d'une inscription OAuth, créé par le contexte {@code identity} (ADR 0008). */
public interface OAuthAccountPort {

    /**
     * Le compte créé, actif si le courriel est vérifié, en attente sinon. Les refus sont ceux de l'inscription
     * (identifiant, nom affiché) et {@code OAUTH_EMAIL_IN_USE} (AUTH-33).
     */
    UserId create(String username, String displayName, String email, boolean emailVerified);
}
