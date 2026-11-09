package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Issue d'une connexion par mot de passe (AUTH-20, AUTH-21). */
public sealed interface LoginOutcome {

    /**
     * Mot de passe juste.
     *
     * @param verified vrai si le compte est actif ; faux s'il est en attente, d'où une session restreinte (AUTH-16)
     */
    record Authenticated(UserId user, boolean verified) implements LoginOutcome {

        public Authenticated {
            Objects.requireNonNull(user, "user");
        }
    }

    /**
     * Connexion refusée, quelle qu'en soit la raison : la réponse ne le dit pas (AUTH-21).
     *
     * @param user compte trouvé, pour le journal (SEC-20) ; {@code null} si la saisie n'en désigne aucun
     */
    record Rejected(@Nullable UserId user) implements LoginOutcome {}
}
