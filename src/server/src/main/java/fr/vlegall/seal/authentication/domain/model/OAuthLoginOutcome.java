package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;

/** Issue d'un retour OAuth réussi chez le fournisseur (AUTH-30 à AUTH-33). */
public sealed interface OAuthLoginOutcome {

    /** AUTH-31 : le sujet est lié à ce compte ; session complète s'il est actif, restreinte s'il attend (AUTH-16). */
    record SignedIn(UserId user, boolean active) implements OAuthLoginOutcome {

        public SignedIn {
            Objects.requireNonNull(user, "user");
        }
    }

    /** AUTH-33 : sujet inconnu, mais son courriel vérifié appartient à un compte actif ; aucune liaison automatique. */
    record EmailInUse() implements OAuthLoginOutcome {}

    /** AUTH-32 : premier passage ; l'inscription OAuth attend le choix de l'identifiant. */
    record Unlinked(ExternalIdentity identity) implements OAuthLoginOutcome {

        public Unlinked {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Le sujet est lié à un compte qui ne peut plus servir (supprimé) : refus, sans détail. */
    record Rejected() implements OAuthLoginOutcome {}
}
