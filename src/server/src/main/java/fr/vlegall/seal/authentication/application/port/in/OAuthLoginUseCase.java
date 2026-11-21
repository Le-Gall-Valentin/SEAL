package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthLoginOutcome;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import org.jspecify.annotations.Nullable;

/** AUTH-30 à AUTH-33 : issue d'un retour OAuth, et journal des refus (SEC-20). */
public interface OAuthLoginUseCase {

    /** Le compte lié à cette identité, l'attente d'un premier passage, ou le refus d'AUTH-33. */
    OAuthLoginOutcome resolve(ExternalIdentity identity);

    /**
     * SEC-20 : un retour OAuth refusé avant d'avoir une identité (state inconnu, jeton invalide, consentement refusé).
     *
     * @param cause cause courte, sans donnée de l'utilisateur
     */
    void rejected(@Nullable OAuthProvider provider, String cause);
}
