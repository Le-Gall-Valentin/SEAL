package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.ResetView;
import java.util.Optional;

/** Lecture d'un lien de réinitialisation, pour l'écran Nouveau-Mot-de-passe (ADR 0022, point 1). */
public interface InspectPasswordResetQuery {

    /** Identifiant, courriel et expiration si le lien est valable ; vide sinon, quelle qu'en soit la raison. */
    Optional<ResetView> inspect(String token);
}
