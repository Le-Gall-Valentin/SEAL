package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthException;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.Optional;

/** AUTH-31 : table {@code oauth_identities}, un compte par couple (fournisseur, identifiant stable). */
public interface OAuthIdentityRepository {

    /** Le compte lié à ce sujet chez ce fournisseur ; jamais une recherche par courriel. */
    Optional<UserId> findUser(OAuthProvider provider, String subject);

    /** AUTH-55, ADR 0034 : vrai si le compte a une identité liée chez ce fournisseur. */
    boolean isLinked(UserId user, OAuthProvider provider);

    /**
     * Lie l'identité au compte, dans la transaction en cours, avec le courriel vu chez le fournisseur.
     *
     * @throws OAuthException.AlreadyLinked si ce sujet est déjà lié, ou si le compte l'est déjà à ce fournisseur
     */
    void link(UserId user, ExternalIdentity identity, Instant linkedAt);
}
