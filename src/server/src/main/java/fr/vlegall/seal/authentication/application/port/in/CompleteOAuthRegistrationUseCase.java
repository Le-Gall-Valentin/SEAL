package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthRegistration;

/** AUTH-32 : choix de l'identifiant au terme d'une inscription OAuth en attente ({@code POST /auth/oauth/complete}). */
public interface CompleteOAuthRegistrationUseCase {

    /**
     * Crée le compte et le lie à l'identité du fournisseur, dans une seule transaction : un refus n'en laisse rien.
     *
     * @throws fr.vlegall.seal.authentication.domain.model.OAuthException.AlreadyLinked si l'identité a été liée
     *     entre-temps
     */
    OAuthRegistration complete(ExternalIdentity identity, String username, String displayName);
}
